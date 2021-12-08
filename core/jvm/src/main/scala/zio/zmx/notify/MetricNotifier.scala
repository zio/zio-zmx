package zio.zmx.notify

import zio._
import zio.stream._
import zio.metrics._

/**
 * A metric notifier manages subscriptions from various clients and generates
 * a Stream of Chunk[MetricState] at regular time intervals. This stream can be
 * used to push data towards push based back-ends such as StatsD or the ZMX developers
 * client.
 */
trait MetricNotifier {

  /**
   * Create a new connection generating a new id. All subscriptions within that
   * connection will report their changes to the same stream
   */
  def connect(): UIO[(String, UStream[Map[MetricKey, MetricState]])]

  /**
   * Create a new subscription within a formerly created connection
   */
  def subscribe(conId: String, subId: String, interval: Duration): UIO[Unit]

  def addMetrics(id: String, keys: Chunk[MetricKey]): UIO[Unit]

  def removeMetrics(id: String, keys: Chunk[MetricKey]): UIO[Unit]

  def changeInterval(id: String, interval: Duration): UIO[Unit]

  def unsubscribe(id: String): UIO[Unit]

  def disconnect(id: String): UIO[Unit]

  def stop(): UIO[Unit]

}

object MetricNotifier {
  def live: ZLayer[Clock with Random, Nothing, MetricNotifier] = (for {
    rnd    <- ZIO.service[Random]
    clk    <- ZIO.service[Clock]
    cmdHub <- Hub.bounded[NotifierCommand](128)
    state  <- Ref.Synchronized.make(NotifierState.empty)
  } yield new MetricNotifierImpl(rnd, clk, state) {}).toLayer

  sealed abstract private[MetricNotifier] class MetricNotifierImpl(
    rnd: Random,
    clk: Clock,
    state: Ref.Synchronized[NotifierState]
  ) extends MetricNotifier {

    def connect(): UIO[(String, UStream[Map[MetricKey, MetricState]])] = for {
      id  <- rnd.nextUUID.map(_.toString())
      _   <- ZIO.logInfo(s"Creating new Client connection <$id>")
      clt <- ConnectedClient.empty(id)
      _   <- state.update(cmdHandler(_, NotifierCommand.Connect(clt)))
    } yield (id, ZStream.fromHub(clt.hub))

    def getUpdates(id: String): UStream[Map[MetricKey, MetricState]] = ???

    def subscribe(conId: String, subId: String, interval: Duration): UIO[Unit] = ???

    def addMetrics(id: String, keys: Chunk[MetricKey]): UIO[Unit] = ???

    def removeMetrics(id: String, keys: Chunk[MetricKey]): UIO[Unit] = ???

    def changeInterval(id: String, interval: Duration): UIO[Unit] = ???

    def unsubscribe(id: String): UIO[Unit] = ???

    def disconnect(id: String): UIO[Unit] = ???

    def stop(): UIO[Unit] = ???

    private val cmdHandler: (NotifierState, NotifierCommand) => NotifierState = (cur, cmd) => {
      ZIO.logInfo(s"Received [$cmd]")
      cmd match {
        case NotifierCommand.Connect(clt) =>
          cur.copy(clients = cur.clients ++ Map(clt.id -> clt))
        case _                            =>
          cur
      }
    }

    // def subscribe(interval: Duration): UIO[(String, UStream[Chunk[MetricState]])] = for {
    //   nextId <- rnd.nextUUID.map(_.toString())
    //   _      <- ZIO.logInfo(s"Creating subscription [$nextId] at interval [$interval]")
    //   f      <- run(nextId).schedule(Schedule.duration(interval)).forkDaemon.provide(ZLayer.succeed(clk))
    //   hub    <- Hub.sliding[Chunk[MetricState]](128)
    //   newSub  = Subscription(
    //               hub,
    //               Chunk.empty,
    //               interval,
    //               f
    //             )
    //   _      <- subscriptions.update(_.updated(nextId, newSub))
    //   str     = ZStream.fromHub(newSub.hub)
    // } yield (nextId, str)

    // def addMetrics(id: String, keys: Chunk[MetricKey]): UIO[Unit] =
    //   updateSubscription(id, s => s.copy(metrics = (s.metrics ++ keys).distinct))

    // def removeMetrics(id: String, keys: Chunk[MetricKey]): UIO[Unit] =
    //   updateSubscription(id, s => s.copy(metrics = s.metrics.filter(k => !keys.contains(k))))

    // def changeInterval(id: String, interval: Duration): UIO[Unit] =
    //   updateSubscription(id, s => s.copy(interval = interval))

    // def unsubscribe(id: String): UIO[Unit] = for {
    //   sub <- subscriptions.get.map(_.get(id))
    //   _   <- ZIO.foreach(sub)(_.stop())
    //   _   <- subscriptions.update(_ - id)
    // } yield ()

    // def stop(): UIO[Unit] = subscriptions.get.map(_.foreach { case (_, s) => s.stop() })

    // private def updateSubscription(id: String, f: Subscription => Subscription): UIO[Unit] =
    //   subscriptions.update { subs =>
    //     subs.get(id) match {
    //       case None    => subs
    //       case Some(s) => subs.updated(id, f(s))
    //     }
    //   }

    // private def updateSubscriptionZIO(id: String)(f: Subscription => UIO[Subscription]) =
    //   subscriptions.updateZIO { subs =>
    //     subs.get(id) match {
    //       case None    => ZIO.succeed(subs)
    //       case Some(s) => f(s).map(s => subs.updated(id, s))
    //     }
    //   }

    // private def run(id: String): UIO[Unit] = updateSubscriptionZIO(id) { sub =>
    //   for {
    //     _     <- ZIO.logInfo(s"Running evaluation for subscription [$id] with [${sub.metrics.size}] metrics")
    //     states = sub.metrics.map(MetricClient.unsafeState).collect { case Some(s) => s }
    //     _     <- sub.hub.publish(states)
    //     f     <- run(id).schedule(Schedule.duration(sub.interval)).forkDaemon.provide(ZLayer.succeed(clk))
    //   } yield sub.copy(fiber = f)
    // }

  }

  private[MetricNotifier] case class Subscription(
    id: String,
    hub: Hub[Map[MetricKey, MetricState]],
    metrics: Chunk[MetricKey],
    interval: Duration,
    fiber: Fiber.Runtime[_, _]
  ) {
    def stop() = fiber.interrupt *> hub.shutdown
  }

  private[MetricNotifier] object Subscription {
    private val defaultInterval = 1.second

    def empty(id: String, clk: Clock): UIO[Subscription] =
      for {
        hub <- Hub.bounded[Map[MetricKey, MetricState]](128)
        f   <- ZIO
                 .log(s"Running subscription <$id>")
                 .schedule(Schedule.duration(defaultInterval))
                 .forkDaemon
                 .provide(ZLayer.succeed(clk))

      } yield Subscription(
        id,
        hub,
        Chunk.empty,
        defaultInterval,
        f
      )
  }

  private[MetricNotifier] case class ConnectedClient(
    id: String,
    subscriptions: Map[String, Subscription],
    hub: Hub[Map[MetricKey, MetricState]]
  )

  private[MetricNotifier] object ConnectedClient {
    def empty(id: String): UIO[ConnectedClient] =
      ZHub.bounded[Map[MetricKey, MetricState]](128).map(ConnectedClient(id, Map.empty, _))
  }

  private[MetricNotifier] case class NotifierState(
    clients: Map[String, ConnectedClient]
  )

  private[MetricNotifier] object NotifierState {
    def empty = NotifierState(Map.empty)
  }

  sealed private[MetricNotifier] trait NotifierCommand

  private[MetricNotifier] object NotifierCommand {
    case class Connect(clt: ConnectedClient) extends NotifierCommand
  }
}
