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
    def flushMetrics(timeout: Duration): ZIO[Clock, Nothing, Unit]
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

    override def eventStream = ZStream
      .repeatEffect(
        for {
          flush <- ZIO.succeed(flushing.get())
          done  <- if (flush) isEmpty else ZIO.succeed(false)
          e     <- (flush, done) match {
                     case (true, true)  => channel.shutdown >>> ZIO.succeed(TimedMetricEvent.empty)
                     case (true, false) =>
                       ZIO.ifM(channel.size.map(_ > 0))(
                         onTrue = channel.take,
                         onFalse = channel.offer(TimedMetricEvent.empty) >>> ZIO.succeed(TimedMetricEvent.empty)
                       )
                     case (false, _)    => channel.take
                   }
        } yield e
      )
      .takeUntil(e =>
        e.event.details match {
          case MetricEventDetails.Empty => true
          case _                        => false
        }
      )

    override def flushMetrics(t: Duration): ZIO[Clock, Nothing, Unit] = {
      def go: ZIO[Clock, Nothing, Unit] = ZIO.ifM(isEmpty)(
        onTrue = ZIO.unit,
        onFalse = go.schedule(Schedule.duration(100.millis)).flatMap(_ => ZIO.unit)
      )

      (for {
        _  <- ZIO.succeed(flushing.set(true))
        f  <- go.fork
        _  <- f.join
        sf <- channel.awaitShutdown.fork
        _  <- sf.join
      } yield ()).timeout(t).map(_ => ())
    }

    private val flushing: AtomicBoolean = new AtomicBoolean(false)

    private def isEmpty = for {
      s <- channel.isShutdown
      r <- if (s) ZIO.succeed(true) else channel.size.map(_ == 0)
    } yield r

  }
}
