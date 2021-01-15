package zio.zmx.metrics

import zio._
import zio.clock._
import zio.duration._
import zio.stream._

import zio.zmx.metrics.MetricsDataModel._

private[zmx] object MetricsChannel {

  trait MetricsChannel {
    def record(m: MetricEvent): ZIO[Any, Nothing, Unit]
    def record(tm: TimedMetricEvent): ZIO[Any, Nothing, Unit]

    def eventStream: ZStream[Any, Nothing, TimedMetricEvent]
    def flushMetrics(timeout: Duration): ZIO[Clock, Nothing, Unit]
  }

  private[zmx] def unsafeMake(): MetricsChannel = {

    val (q, f) = Runtime.default.unsafeRun(
      Queue.unbounded[TimedMetricEvent].zip(Ref.make(false))
    )

    new MetricsChannelImpl(Clock.Service.live, q, f)
  }

  private final class MetricsChannelImpl(
    clockSvc: Clock.Service,
    channel: Queue[TimedMetricEvent],
    flushing: Ref[Boolean]
  ) extends MetricsChannel {

    override def record(m: MetricEvent): ZIO[Any, Nothing, Unit] =
      ZIO.ifM(flushing.get)(
        ZIO.unit,
        clockSvc.instant.flatMap(now => record(TimedMetricEvent(m, now)))
      )

    override def record(tm: TimedMetricEvent): ZIO[Any, Nothing, Unit] =
      channel.offer(tm).map(_ => ())

    override def eventStream = ZStream
      .repeatEffect(channel.take)
      .takeUntilM(e =>
        e.event.details match {
          case MetricEventDetails.Empty => channel.shutdown >>> ZIO.succeed(true)
          case _                        => ZIO.succeed(false)
        }
      )

    override def flushMetrics(t: Duration): ZIO[Clock, Nothing, Unit] = {
      def go: ZIO[Clock, Nothing, Unit] = ZIO.ifM(isEmpty)(
        onTrue = ZIO.unit,
        onFalse = go.schedule(Schedule.duration(100.millis)).flatMap(_ => ZIO.unit)
      )

      (for {
        _ <- record(MetricEvent.empty)
        _ <- flushing.set(true)
        _ <- go
        _ <- channel.awaitShutdown
      } yield ()).timeout(t).map(_ => ())
    }

    private def isEmpty =
      ZIO.ifM(channel.isShutdown)(
        ZIO.succeed(true),
        channel.size.map(_ == 0)
      )
  }
}
