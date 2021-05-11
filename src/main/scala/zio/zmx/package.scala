package zio

import java.time.Instant
import zio.zmx.internal.ConcurrentState
import zio.zmx.state.MetricType

package object zmx {

  type Label = (String, String)

  /**
   *  Report a named Guage with an absolute value.
   */
  def setGauge(key: MetricKey.Gauge, value: Double): ZIO[Any, Nothing, Any] =
    metricState.getGauge(key).set(value)

  /**
   * Report a relative change for a named Gauge with a given delta.
   */
  def adjustGauge(key: MetricKey.Gauge, value: Double): ZIO[Any, Nothing, Any] =
    metricState.getGauge(key).adjust(value)

  /**
   * Increase a named counter by some value.
   */
  def incrementCounter(c: MetricKey.Counter, value: Double): ZIO[Any, Nothing, Any] =
    metricState.getCounter(c).increment(value)

  /**
   * Observe a value and feed it into a histogram
   */
  def observeInHistogram(key: MetricKey.Histogram, v: Double): ZIO[Any, Nothing, Any] =
    metricState.getHistogram(key).observe(v)

  /**
   * Observe a value and feed it into a summary
   */
  def observeInSummary(key: MetricKey.Summary, v: Double): ZIO[Any, Nothing, Any] =
    metricState.getSummary(key).observe(v, Instant.now())

  /**
   * Record a String to track the number of different values within the given name.
   */
  def observeString(key: MetricKey.Occurence, v: String): ZIO[Any, Nothing, Any] =
    incrementCounter(key.counterKey(v), 1.0d)

  private[zmx] val metricState: ConcurrentState =
    new ConcurrentState
}
