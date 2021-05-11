package zio

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
    ???

  /**
   * Record a String to track the number of different values within the given name.
   */
  def observeString(name: String, v: String, tags: Label*): ZIO[Any, Nothing, Any] =
    ???

  // TODO: Delete in favor of metric aspects
  implicit class MetricsSyntax[R, E, A](zio: ZIO[R, E, A]) {
    def counted(c: MetricKey.Counter)                                                    =
      zio <* incrementCounter(c, 1.0d)
    def gaugedAbsolute(key: MetricKey.Gauge)(implicit ev: A <:< Double): ZIO[R, E, A]    =
      zio.tap(a => setGauge(key, ev(a)))
    def gaugedRelative(key: MetricKey.Gauge)(implicit ev: A <:< Double): ZIO[R, E, A]    =
      zio.tap(a => adjustGauge(key, ev(a)))
    def observed(name: String, tags: Label*)(implicit ev: IsObservable[A]): ZIO[R, E, A] =
      ev.observe(zio, name, tags: _*)
    def obbserved(key: MetricKey.Histogram)(implicit
      ev: A <:< Double
    ): ZIO[R, E, A]                                                                      =
      zio.tap(a => observeInHistogram(key, ev(a)))
  }

  private[zmx] val metricState: ConcurrentState =
    new ConcurrentState
}
