package zio.zmx.metrics

import zio._

trait Gauge {
  def set(value: Double): UIO[Any]
  def adjust(value: Double): UIO[Any]
}

object Gauge {

  val none: Gauge =
    new Gauge {
      def set(value: Double): UIO[Any]    = ZIO.unit
      def adjust(value: Double): UIO[Any] = ZIO.unit
    }
}
