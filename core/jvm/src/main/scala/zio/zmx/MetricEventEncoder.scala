package zio.zmx

import zio._

trait MetricEventEncoder[A] {
  
  def encode(event: MetricEvent): ZIO[Any, Throwable, Chunk[A]]
}
