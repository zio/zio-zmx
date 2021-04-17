package zio.zmx.metrics

import zio._

trait StringHistogram {
  def observe(value: Double): UIO[Any]
}

object StringHistogram {

  val none: StringHistogram =
    new StringHistogram {
      def observe(value: Double): UIO[Any] = ZIO.unit
    }
}
