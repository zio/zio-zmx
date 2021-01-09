package zio.zmx.prometheus

import zio._
import zio.clock.Clock
import zio.zmx.metrics._
import MetricsDataModel._

final class PrometheusInstrumentaion(
  registry: PrometheusRegistry
) extends Instrumentation[String] {

  override def handleMetric(me: TimedMetricEvent) = registry.update(me)

  override def report: ZIO[Clock, Nothing, Option[String]] = for {
    metrics <- registry.list
    now     <- clock.instant
    encoded  = PrometheusEncoder.encode(metrics, now)
  } yield Some(encoded)

}
