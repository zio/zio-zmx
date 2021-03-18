package zio.zmx.internal

import zio.zmx.Label
import java.util.concurrent.ConcurrentHashMap
import zio.zmx.HistogramType
import zio.Chunk
import zio.zmx.state.MetricState
import java.util.concurrent.atomic.{ AtomicReference, DoubleAdder }

final case class ConcurrentState() {

  val map: ConcurrentHashMap[String, ConcurrentMetricState] =
    new ConcurrentHashMap[String, ConcurrentMetricState]()

  /**
   *  Report a named Guage with an absolute value.
   */
  def setGauge(name: String, v: Double, tags: Label*): Unit = {
    var value = map.get(name)
    if (value eq null) {
      val gauge = ConcurrentMetricState.Gauge(name, "", Chunk(tags: _*), new AtomicReference(0.0))
      map.putIfAbsent(name, gauge)
      value = map.get(name)
    }
    value match {
      case gauge: ConcurrentMetricState.Gauge => gauge.set(v)
      case _                                  =>
    }
  }

  /**
   * Report a relative change for a named Gauge with a given delta.
   */
  def adjustGauge(name: String, v: Double, tags: Label*): Unit = {
    var value = map.get(name)
    if (value eq null) {
      val gauge = ConcurrentMetricState.Gauge(name, "", Chunk(tags: _*), new AtomicReference(0.0))
      map.putIfAbsent(name, gauge)
      value = map.get(name)
    }
    value match {
      case gauge: ConcurrentMetricState.Gauge => gauge.adjust(v)
      case _                                  =>
    }
  }

  /**
   * Increase a named counter by some value.
   */
  def incrementCounter(name: String, v: Double, tags: Label*): Unit = {
    var value = map.get(name)
    if (value eq null) {
      val counter = ConcurrentMetricState.Counter(name, "", Chunk(tags: _*), new DoubleAdder)
      map.putIfAbsent(name, counter)
      value = map.get(name)
    }
    value match {
      case counter: ConcurrentMetricState.Counter => counter.increment(v)
      case _                                      =>
    }
  }

  /**
   * Observe a value and feed it into a histogram
   */
  def observeDouble(name: String, v: Double, ht: HistogramType, tags: Label*): Unit =
    ???

  /**
   * Record a String to track the number of different values within the given name.
   */
  def observeString(name: String, v: String, tags: Label*): Unit =
    ???

  def snapshot(): Map[String, MetricState] =
    ???
}
