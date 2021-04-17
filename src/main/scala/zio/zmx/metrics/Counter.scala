package zio.zmx.metrics

import zio._
import zio.zmx.metricState

trait Counter {
  def increment(value: Double): UIO[Any]
}

object Counter {

  def apply(name: String): Counter =
    metricState.getCounter(name)

  val none: Counter =
    new Counter {
      def increment(value: Double): UIO[Any] = ZIO.unit
    }
}
