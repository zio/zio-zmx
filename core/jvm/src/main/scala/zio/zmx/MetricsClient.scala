package zio.zmx

import zio._

trait MetricsClient {
  def snapshot: ZIO[Any, Nothing, MetricSnapshot]
}
