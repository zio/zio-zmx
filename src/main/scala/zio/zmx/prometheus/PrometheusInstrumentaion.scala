package zio.zmx.metrics

import zio._
import zio.zmx.MetricsDataModel._

private[metrics] class PrometheusInstrumentaion extends ZMetrics.Service {
  override def counter(name: String): ZIO[Any, Nothing, Option[Metric.Counter]]        = ???
  override def increment(m: Metric.Counter): ZIO[Any, Nothing, Option[Metric.Counter]] = ???
}
