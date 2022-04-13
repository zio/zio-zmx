package zio.zmx

import zio._

trait MetricPublisher[A] {

  def publish(metrics: Chunk[A]): ZIO[Any, Throwable, Unit]
  
}
