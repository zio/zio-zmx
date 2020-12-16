package zio.zmx.prometheus

import zio._
import zio.clock.Clock
import zio.zmx.metrics._
import MetricsDataModel._

final class PrometheusInstrumentaion(
  registry: PrometheusRegistry
) extends Instrumentation {

  override def handleMetric = m =>
    m.event.details match {
      case c: MetricEventDetails.Count =>
        registry.update(PMetric.counter(m.event.name, "", m.event.tags))(cnt => PMetric.incCounter(cnt, c.v))
    }

  override def report: ZIO[Clock, Nothing, String] = for {
    metrics <- registry.list
    now     <- clock.instant
    encoded  = PrometheusEncoder.encode(metrics, now)
  } yield encoded

}
