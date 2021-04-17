package zio.zmx.metrics

import zio._
import zio.zmx._

import java.time.Duration

/**
 * A `Summary` represents a sliding window of a time series along with certain
 * metrics for the sliding window, referred to as quantiles. Quantiles
 * describe specified percentiles of the sliding window that are of interest.
 * For example, if we were using a summary to track the response time for
 * requests over the last hour then we might be interested in the 50th
 * percentile, 90th percentile, 95th percentile, and 99th percentile for
 * response times.
 */
trait Summary {

  /**
   * Adds the specified value to the time series represented by the summary.
   */
  def observe(value: Double): UIO[Any]
}

object Summary {

  /**
   * Constructs a new summary with the specified name, maximum age, maximum
   * size, quantiles, and labels. The quantiles must be between 0.0 and 1.0.
   */
  def apply(name: String, maxAge: Duration, maxSize: Int, quantiles: Chunk[Double], tags: Label*): Summary =
    metricState.getSummary(name, maxAge, maxSize, quantiles, tags: _*)

  /**
   * A summary that does nothing.
   */
  val nothing: Summary =
    new Summary {
      def observe(value: Double): UIO[Any] =
        ZIO.unit
    }
}
