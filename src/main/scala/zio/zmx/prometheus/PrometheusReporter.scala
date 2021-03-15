package zio.zmx.prometheus

import zio.zmx.metrics._
import MetricsDataModel._
import zio.zmx.MetricsReporter

final case class PrometheusReporter(registry: PrometheusRegistry) extends MetricsReporter {
  override def report(event: MetricEvent) =
    registry.update(event)
}
