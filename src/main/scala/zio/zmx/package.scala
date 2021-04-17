package zio

import zio.zmx.internal.ConcurrentState

package object zmx {

  type Label = (String, String)

  /**
   *  Report a named Guage with an absolute value.
   */
  def setGauge(name: String, value: Double, tags: Label*): ZIO[Any, Nothing, Any] =
    metricState.getGauge(name, tags: _*).set(value)

  /**
   * Report a relative change for a named Gauge with a given delta.
   */
  def adjustGauge(name: String, value: Double, tags: Label*): ZIO[Any, Nothing, Any] =
    metricState.getGauge(name, tags: _*).adjust(value)

  /**
   * Increase a named counter by some value.
   */
  def incrementCounter(name: String, value: Double, tags: Label*): ZIO[Any, Nothing, Any] =
    metricState.getCounter(name, tags: _*).increment(value)

  /**
   * Observe a value and feed it into a histogram
   */
  def observeDouble(name: String, v: Double, ht: HistogramType, tags: Label*): ZIO[Any, Nothing, Any] =
    ???

  /**
   * Record a String to track the number of different values within the given name.
   */
  def observeString(name: String, v: String, tags: Label*): ZIO[Any, Nothing, Any] =
    ???

  implicit class MetricsSyntax[R, E, A](zio: ZIO[R, E, A]) {
    def counted(name: String, tags: Label*)                                                 =
      zio <* incrementCounter(name, 1.0d, tags: _*)
    def gaugedAbsolute(name: String, tags: Label*)(implicit ev: A <:< Double): ZIO[R, E, A] =
      zio.tap(a => setGauge(name, ev(a), tags: _*))
    def gaugedRelative(name: String, tags: Label*)(implicit ev: A <:< Double): ZIO[R, E, A] =
      zio.tap(a => adjustGauge(name, ev(a), tags: _*))
    def observed(name: String, tags: Label*)(implicit ev: IsObservable[A]): ZIO[R, E, A]    =
      ev.observe(zio, name, tags: _*)
    def observed(name: String, histogramType: HistogramType, tags: Label*)(implicit
      ev: A <:< Double
    ): ZIO[R, E, A]                                                                         =
      zio.tap(a => observeDouble(name, ev(a), histogramType, tags: _*))
  }

  private[zmx] val metricState: ConcurrentState =
    new ConcurrentState
}
