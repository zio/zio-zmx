package zio.zmx.notify

import java.time.Instant

import zio._
import zio.stream._
import zio.metrics._

case class MetricsUpdate(
  clt: String,
  subId: String,
  when: Instant,
  states: Set[MetricPair.Untyped]
)

/**
 * A metric notifier manages subscriptions from various clients and generates
 * a Stream of Chunk[MetricState] at regular time intervals. This stream can be
 * used to push data towards push based back-ends such as StatsD or the ZMX developers
 * client.
 */
trait MetricNotifier {

  /**
   * Create a new connection generating a new id. All subscriptions within that
   * connection will report their changes to the same stream.
   * Secondly we create a Stream where we publish the currently discovered metricKeys
   * on a regular basis.
   */
  def connect(): UIO[(String, UStream[MetricsUpdate], UStream[Seq[MetricKey.Untyped]])]

  /**
   * Create a new subscription within a formerly created connection
   */
  def subscribe(conId: String, subId: String, keys: Chunk[MetricKey.Untyped], interval: Duration): UIO[Unit]

  def unsubscribe(conId: String, id: String): UIO[Unit]

  def disconnect(id: String): UIO[Unit]

  def stop(): UIO[Unit]

}

object MetricNotifier {

  def live: ZLayer[Clock with Random, Nothing, MetricNotifier] = (for {
    rnd   <- ZIO.service[Random]
    clk   <- ZIO.service[Clock]
    state <- Ref.Synchronized.make(NotifierState.empty)
  } yield new MetricNotifierImpl(rnd, clk, state) {}).toLayer

  sealed abstract private[MetricNotifier] class MetricNotifierImpl(
    rnd: Random,
    clk: Clock,
    state: Ref.Synchronized[NotifierState]
  ) extends MetricNotifier {

    // TODO: Type construct to create the final Stream 
    def connect(): UIO[(String, UStream[MetricsUpdate], UStream[Seq[MetricKey.Untyped]])] = for {
      id  <- rnd.nextUUID.map(_.toString())
      _   <- ZIO.logInfo(s"Creating new Client connection <$id>")
      clt <- ConnectedClient.empty(id, clk)
      _   <- state.update(s => s.copy(clients = s.clients.updated(id, clt)))
      _   <- state.get.map(_.clients.size).flatMap(n => ZIO.logInfo(s"Server now has <$n> connected clients"))
    } yield (id, ZStream.fromHub(clt.metrics), ZStream.empty) // ZStream.fromHub(clt.keys))

    def subscribe(conId: String, subId: String, keys: Chunk[MetricKey.Untyped], interval: Duration): UIO[Unit] = for {
      _ <- ZIO.logInfo(s"Setting subscription <$conId><$subId> with <${keys.size}> keys at <$interval>")
      _ <- state.updateZIO(_.setSubscription(conId, subId, keys, interval))
    } yield ()

    def unsubscribe(conId: String, id: String): UIO[Unit] = for {
      _ <- ZIO.logInfo(s"Removing subscription <$conId><$id>")
      _ <- state.updateZIO(state => state.removeSubscription(conId, id))
    } yield ()

    def disconnect(id: String): UIO[Unit] = state.updateZIO { s =>
      s.clients.get(id) match {
        case None      => ZIO.succeed(s)
        case Some(clt) => clt.stop().as(s.copy(clients = s.clients - id))
      }
    }

    def stop(): UIO[Unit] = state.get.flatMap(s => ZIO.foreach(s.clients.values)(_.stop())).as(())

  }

  private[MetricNotifier] case class Subscription(
    subId: String,
    fiber: Fiber.Runtime[_, _]
  ) {
    def stop() = fiber.interrupt
  }

  private[MetricNotifier] case class ConnectedClient(
    id: String,
    subscriptions: Map[String, Subscription],
    metrics: Hub[MetricsUpdate],
    keys: Hub[Set[MetricKey.Untyped]],
    clk: Clock,
    fiber: Fiber.Runtime[_, _]
  ) { self =>
    def stop() =
      ZIO.logInfo(s"Closing Client Connection <$id>") *>
        ZIO.foreach(subscriptions.values)(s => s.stop()) *>
        metrics.shutdown *>
        keys.shutdown *>
        fiber.interrupt

    def removeSubscription(subId: String): ZIO[Any, Nothing, ConnectedClient] =
      subscriptions.get(subId) match {
        case None    => ZIO.succeed(self)
        case Some(s) => s.stop().as(self.copy(subscriptions = self.subscriptions - subId))
      }

    def setSubscription(
      subId: String,
      keys: Chunk[MetricKey.Untyped],
      interval: Duration
    ): ZIO[Any, Nothing, ConnectedClient] = {

      def selectStates: ZIO[Any, Nothing, Set[MetricPair.Untyped]] =
        ZIO
          .succeed(MetricClient.unsafeSnapshot())
          .map(_.filter { pair =>
            keys.contains(pair.metricKey)
          })

      val run: ZIO[Any, Nothing, Unit] = for {
        states <- selectStates
        _      <- ZIO.logInfo(s"Found <${states.size}> states for subscription <$id><$subId>")
        msg    <- clk.instant.map(MetricsUpdate(id, subId, _, states))
        _      <- metrics.publish(msg)
      } yield ()

      for {
        newClt <- removeSubscription(subId)
        f      <- clk.schedule(run)(Schedule.duration(1.milli) ++ Schedule.spaced(interval)).forkDaemon
        sub     = Subscription(subId, f)
      } yield self.copy(subscriptions = self.subscriptions.updated(subId, sub))
    }
  }

  private[MetricNotifier] object ConnectedClient {
    def empty(id: String, clk: Clock): UIO[ConnectedClient] = for {
      metrics <- ZHub.bounded[MetricsUpdate](128)
      keys    <- ZHub.bounded[Set[MetricKey.Untyped]](128)
      f       <- ZIO
                   .succeed(MetricClient.unsafeSnapshot().map(_.metricKey))
                   .tap(m => ZIO.logInfo(s"Discovered <${m.size}> metric keys"))
                   // TODO: How to construct the hub
                   //.flatMap(keySet => keys.publish(keySet))
                   .schedule(Schedule.duration(1.milli) ++ Schedule.spaced(5.seconds))
                   .forkDaemon
                   .provide(ZLayer.succeed(clk))
    } yield ConnectedClient(id, Map.empty, metrics, keys, clk, f)
  }

  private[MetricNotifier] case class NotifierState(
    clients: Map[String, ConnectedClient]
  ) { self =>

    def removeSubscription(clt: String, subId: String): ZIO[Any, Nothing, NotifierState] =
      clients.get(clt) match {
        case None    => ZIO.succeed(self)
        case Some(c) =>
          c.removeSubscription(subId).map(clients.updated(clt, _)).map(c => self.copy(clients = c))
      }

    def setSubscription(
      clt: String,
      subId: String,
      keys: Chunk[MetricKey.Untyped],
      interval: Duration
    ): ZIO[Any, Nothing, NotifierState] =
      clients.get(clt) match {
        case None    => ZIO.succeed(self)
        case Some(c) =>
          c.setSubscription(subId, keys, interval).map(clients.updated(clt, _)).map(c => self.copy(clients = c))
      }
  }

  private[MetricNotifier] object NotifierState {
    def empty = NotifierState(Map.empty)
  }
}
