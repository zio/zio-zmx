package zio

import zio.zmx.metrics._
import zio.zmx.metrics._

package object zmx {

  type Label = (String, String)

  /**
   *  Report a named Guage with an absolute value.
   */
  def setGauge(name: String, v: Double, tags: Label*): ZIO[Any, Nothing, Any] =
    metricsChannel.offer(gauge(name, v, tags: _*))

  /**
   * Report a relative change for a named Gauge with a given delta.
   */
  def adjustGauge(name: String, v: Double, tags: Label*): ZIO[Any, Nothing, Any] =
    metricsChannel.offer(gaugeChange(name, v, tags: _*))

  /**
   * Increase a named counter by some value.
   */
  def incrementCounter(name: String, v: Double, tags: Label*): ZIO[Any, Nothing, Any] =
    metricsChannel.offer(count(name, v, tags: _*))

  /**
   * Observe a value and feed it into a histogram
   */
  def observeDouble(name: String, v: Double, ht: HistogramType, tags: Label*): ZIO[Any, Nothing, Any] =
    metricsChannel.offer(observe(name, v, ht, tags: _*))

  /**
   * Record a String to track the number of different values within the given name.
   */
  def observeString(name: String, v: String, tags: Label*): ZIO[Any, Nothing, Any] =
    metricsChannel.offer(observe(name, v, tags: _*))

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

  private[zmx] val metricsChannel: Queue[MetricEvent] =
    Runtime.default.unsafeRun(Queue.sliding[MetricEvent](16384))

  private def count(name: String, v: Double, tags: Label*): MetricEvent =
    MetricEvent(name, MetricEventDetails.count(v), tags: _*)

  private def gauge(name: String, v: Double, tags: Label*): MetricEvent =
    MetricEvent(name, MetricEventDetails.gaugeChange(v, false), tags: _*)

  private def gaugeChange(name: String, v: Double, tags: Label*): MetricEvent =
    MetricEvent(name, MetricEventDetails.gaugeChange(v, true), tags: _*)

  private def observe(name: String, v: Double, ht: HistogramType, tags: Label*) =
    MetricEvent(name, MetricEventDetails.observe(v, ht), tags: _*)

  private def observe(name: String, key: String, tags: Label*) =
    MetricEvent(name, MetricEventDetails.observe(key), tags: _*)
}
