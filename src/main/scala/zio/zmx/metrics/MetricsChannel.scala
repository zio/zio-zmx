package zio.zmx.metrics

import java.util.concurrent.atomic.AtomicBoolean

import zio._
import zio.clock.Clock
import zio.duration._
import zio.stream._

import zio.zmx.metrics.MetricsDataModel._

object MetricsChannel {

  def record(m: MetricEvent) =
    if (!flushing.get) channel.service.flatMap(_.offer(m))
    else ZIO.unit

  def recordOption(m: Option[MetricEvent]) = ZIO.foreach(m)(m => record(m))

  def eventStream = ZStream.repeatEffect(channel.service.flatMap(q => q.take))
  def isEmpty     = for {
    ch <- channel.service
    s  <- ch.size
  } yield s <= 0

  def flushMetrics: ZIO[Clock, Nothing, Unit] = {
    def go: ZIO[Clock, Nothing, Unit] = ZIO.ifM(MetricsChannel.isEmpty)(
      onTrue = ZIO.unit,
      onFalse = go.schedule(Schedule.duration(100.millis)).flatMap(_ => ZIO.unit)
    )

    for {
      _ <- ZIO.succeed(flushing.set(true))
      f <- go.fork
      _ <- f.join
      _ <- channel.service.flatMap(_.shutdown)
    } yield ()
  }

  private lazy val channel = new Object with SingletonService[Queue[MetricEvent]] {
    override private[zmx] def makeService = Queue.unbounded[MetricEvent]
  }

  private val flushing: AtomicBoolean = new AtomicBoolean(false)

}
