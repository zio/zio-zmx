package zio.zmx.prometheus

import zio._
import zio.clock.Clock
import zio.zmx.metrics._
import zio.zmx.prometheus.PrometheusJsonEncoder._
import MetricsDataModel._
import zio.json._

final class PrometheusInstrumentaion(
  registry: PrometheusRegistry
) extends Instrumentation {

  override def handleMetric(me: TimedMetricEvent) = registry.update(me)

  override def report: ZIO[Clock, Nothing, Option[String]] = for {
    metrics <- registry.list
    now     <- clock.instant
    encoded  = PrometheusEncoder.encode(metrics, now)
  } yield Some(encoded)

  override def reportJson: ZIO[Clock, Nothing, Option[String]] = for {
    metrics <- registry.list
    encoded  = metrics.toJsonPretty
  } yield Some(encoded)

}
