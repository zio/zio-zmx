package zio.zmx.metrics

import java.util.concurrent.atomic.AtomicBoolean

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
    def flushMetrics(timeout: Duration): ZIO[Clock, Nothing, Int]
  }

  def make(layer: ZLayer[Any, Nothing, Clock]): ZIO[Any, Nothing, MetricsChannel] = (for {
    clockSvc <- ZIO.service[Clock.Service]
    ch       <- Queue.unbounded[TimedMetricEvent]
    r         = new MetricsChannelImpl(clockSvc, ch)
  } yield r).provideLayer(layer)

  private final class MetricsChannelImpl(clockSvc: Clock.Service, channel: Queue[TimedMetricEvent])
      extends MetricsChannel {

    override def record(m: MetricEvent): ZIO[Any, Nothing, Unit] =
      if (!flushing.get) (for {
        now <- clockSvc.instant
        _   <- channel.offer(TimedMetricEvent(m, now))
      } yield ())
      else ZIO.unit

    override def record(tm: TimedMetricEvent): ZIO[Any, Nothing, Unit] = channel.offer(tm).map(_ => ())

    override def eventStream = ZStream.repeatEffect(channel.take)

    // TODO: Add a timeout here
    override def flushMetrics(t: Duration): ZIO[Clock, Nothing, Int] = {
      def go: ZIO[Clock, Nothing, Unit] = ZIO.ifM(isEmpty)(
        onTrue = ZIO.unit,
        onFalse = go.schedule(Schedule.duration(100.millis)).flatMap(_ => ZIO.unit)
      )

      (
        (for {
          _ <- ZIO.succeed(flushing.set(true))
          f <- go.fork
          _ <- f.join
        } yield 0).timeout(t).flatMap(_ => channel.size)
      ) <* channel.shutdown
    }

    private val flushing: AtomicBoolean = new AtomicBoolean(false)

    private def isEmpty = channel.size.map(_ <= 0)

  }
}
