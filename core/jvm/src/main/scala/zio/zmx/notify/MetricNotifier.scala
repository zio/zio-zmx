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
  } yield new MetricNotifierImpl(rnd, clk, cmdHub) {}).toLayer

  sealed abstract private[MetricNotifier] class MetricNotifierImpl(
    rnd: Random,
    clk: Clock,
    cmds: Hub[NotifierCommand]
  ) extends MetricNotifier {

    def connect(): UIO[(String, UStream[Map[MetricKey, MetricState]])] = ???

    def subscribe(conId: String, subId: String, interval: Duration): UIO[Unit] = ???

    def addMetrics(id: String, keys: Chunk[MetricKey]): UIO[Unit] = ???

    def removeMetrics(id: String, keys: Chunk[MetricKey]): UIO[Unit] = ???

    def changeInterval(id: String, interval: Duration): UIO[Unit] = ???

    def unsubscribe(id: String): UIO[Unit] = ???

    def disconnect(id: String): UIO[Unit] = ???

    def stop(): UIO[Unit] = ???

    // private val cmdHandler                                                        =
    //   ZStream.fromHub(cmds).runFold[NotifierState](NotifierState(Map.empty)) { case (s, cmd) => s }

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
    hub: Hub[Chunk[MetricState]],
    metrics: Chunk[MetricKey],
    interval: Duration,
    fiber: Fiber.Runtime[_, _]
  ) {
    def stop() = fiber.interrupt *> hub.shutdown
  }

  private[MetricNotifier] case class NotifierState(
    connections: Map[String, Map[String, Subscription]]
  )

  sealed private[MetricNotifier] trait NotifierCommand

  private[MetricNotifier] object NotifierCommand {
    case object Connect extends NotifierCommand
  }
}
