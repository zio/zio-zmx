package zio.zmx

import zio._

trait MetricsClient[+T] {
  def snapshot: ZIO[Any, Nothing, MetricSnapshot[T]]
}
