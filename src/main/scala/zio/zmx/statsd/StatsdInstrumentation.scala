package zio.zmx.statsd

import zio._

import zio.zmx.statsd.StatsdDataModel._
import zio.zmx.MetricsConfig
import zio.zmx.metrics.MetricsDataModel._
import zio.zmx.metrics.Instrumentation

final class StatsdInstrumentation(config: MetricsConfig) extends Instrumentation {

  private val statsdClient = StatsdClient.live(config).orDie

  def handleMetric(m: MetricEvent): ZIO[Any, Nothing, Unit] = m.details match {
    case c: MetricEventDetails.Count => send(Metric.Counter(m.name, c.v, 1d, Chunk.empty))
  }

  private def send(m: Metric[_]): ZIO[Any, Nothing, Unit] = (for {
    clt <- ZIO.service[StatsdClient.StatsdClientSvc]
    buf  = Chunk.fromArray(StatsdEncoder.encode(m).getBytes())
    _   <- clt.write(buf)
  } yield ()).provideLayer(statsdClient).catchAll(_ => ZIO.succeed(()))

}
