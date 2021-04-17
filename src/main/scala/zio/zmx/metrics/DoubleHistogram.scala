package zio.zmx.metrics

import zio._

trait DoubleHistogram {
  def observe(value: Double): UIO[Any]
}

object DoubleHistogram {

  val none: DoubleHistogram =
    new DoubleHistogram {
      def observe(value: Double): UIO[Any] = ZIO.unit
    }
}
