package zio.zmx.metrics

import zio._
import zio.zmx._

/**
 * A `Gauge` is a metric representing a single numerical value that may be set
 * or adjusted. A typical use of this metric would be to track the current
 * memory usage. With a guage the quantity of interest is the current value,
 * as opposed to a counter where the quantity of interest is the cumulative
 * values over time.
 */
trait Gauge {

  /**
   * Sets the counter to the specified value.
   */
  def set(value: Double): UIO[Any]

  /**
   * Adjusts the counter by the specified amount.
   */
  def adjust(value: Double): UIO[Any]
}

object Gauge {

  /**
   * Constructs a gauge with the specified name and labels.
   */
  def apply(name: String, tags: Label*): Gauge =
    metricState.getGauge(name, tags: _*)

  /**
   * A guage that does nothing.
   */
  val none: Gauge =
    new Gauge {
      def set(value: Double): UIO[Any]    =
        ZIO.unit
      def adjust(value: Double): UIO[Any] =
        ZIO.unit
    }
}
