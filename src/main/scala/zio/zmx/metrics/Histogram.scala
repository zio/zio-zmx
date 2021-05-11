package zio.zmx.metrics

import zio._
import zio.zmx._

/**
 * A `Histogram` is a metric representing a collection of numerical with the
 * distribution of the cumulative values over time. A typical use of this
 * metric would be to track the time to serve requests. Histograms allow
 * visualizing not only the value of the quantity being measured but its
 * distribution. Histograms are constructed with user specified boundaries
 * which describe the buckets to aggregate values into.
 */
trait Histogram {

  /**
   * Adds the specified value to the distribution of values represented by the
   * histogram.
   */
  def observe(value: Double): UIO[Any]
}

object Histogram {

  def apply(key: MetricKey.Histogram): Histogram =
    metricState.getHistogram(key)

  /**
   * Constructs a histogram with the specified name, boundaries, and labels.
   * The boundaries must be in strictly increasing order.
   */
  def apply(name: String, boundaries: Chunk[Double], tags: Label*): Histogram =
    apply(MetricKey.Histogram(name, boundaries, tags:_*))

  /**
   * A histogram that does nothing.
   */
  val none: Histogram =
    new Histogram {
      def observe(value: Double): UIO[Any] =
        ZIO.unit
    }
}
