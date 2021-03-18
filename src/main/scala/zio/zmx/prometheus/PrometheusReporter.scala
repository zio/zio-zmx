package zio.zmx.prometheus

import zio.zmx.metrics._
import zio.zmx.MetricsReporter
import zio.UIO
import zio.zmx.state.MetricState

final case class PrometheusReporter(registry: PrometheusRegistry) extends MetricsReporter {
  def report(event: MetricEvent)              =
    registry.update(event)
  val snapshot: UIO[Map[String, MetricState]] =
    UIO.succeedNow(Map.empty)
}
