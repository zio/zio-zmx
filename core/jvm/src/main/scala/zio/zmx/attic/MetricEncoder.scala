package zio.zmx.attic

import zio._
import zio.metrics.MetricPair

trait MetricEncoder[A] {

  def encodeMetric(metric: MetricPair.Untyped, timestamp: Long): ZIO[Any, Throwable, Chunk[A]]
}
