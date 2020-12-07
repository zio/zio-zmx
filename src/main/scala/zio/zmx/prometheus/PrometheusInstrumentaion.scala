package zio.zmx.prometheus

import zio._
import zio.zmx.metrics._

private[zmx] object PrometheusInstrumentaion {

  def make = PrometheusRegistry.make.map(r => new PrometheusInstrumentaion(r))

}

private[zmx] final class PrometheusInstrumentaion private (
  registry: PrometheusRegistry
) extends ZMetrics.Service {

  override def increment(
    name: String
  ): ZIO[Any, Nothing, Unit] = registry.update[PMetric.Counter](name)(cnt => PMetric.incCounter(cnt))

}
