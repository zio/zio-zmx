package zio.zmx.statsd

import zio._

import zio.zmx.statsd.StatsdDataModel._
import zio.zmx.MetricsConfig
import zio.zmx.metrics.MetricsDataModel._
import zio.zmx.metrics.Instrumentation

final class StatsdInstrumentation(config: MetricsConfig) extends Instrumentation {

  private val statsdClient = StatsdClient.live(config).orDie

  def handleMetric = m =>
    m.details match {
      case c: MetricEventDetails.Count => send(Metric.Counter(m.name, c.v, 1d, m.tags))
    }

  private def send(m: Metric[_]): ZIO[Any, Nothing, Unit] = (for {
    clt <- ZIO.service[StatsdClient.StatsdClientSvc]
    data = StatsdEncoder.encode(m)
    _   <- clt.write(Chunk.fromArray(data.getBytes()))
  } yield ()).provideLayer(statsdClient).catchAll(_ => ZIO.succeed(()))

}
