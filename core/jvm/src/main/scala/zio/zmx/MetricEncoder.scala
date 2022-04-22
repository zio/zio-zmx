package zio.zmx

import zio._

trait MetricEncoder[A] {

  def encodeMetric(event: MetricEvent): ZIO[Any, Throwable, Chunk[A]]
}
