package zio.zmx.metrics

import zio.ZIO

private[metrics] class StatsdInstrumentation extends ZMetrics.Service {
  override def counter(name: String): ZIO[Any, Nothing, Option[Metric.Counter]]        = ???
  override def increment(m: Metric.Counter): ZIO[Any, Nothing, Option[Metric.Counter]] = ???
}
