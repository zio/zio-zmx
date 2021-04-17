package zio.zmx.internal

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{ AtomicReference, AtomicReferenceArray, DoubleAdder, LongAdder }

import zio._
import zio.zmx._
import zio.zmx.metrics._
import zio.zmx.state.MetricState

class ConcurrentState {

  val map: ConcurrentHashMap[String, ConcurrentMetricState] =
    new ConcurrentHashMap[String, ConcurrentMetricState]()

  def getGauge(name: String, tags: Label*): Gauge = {
    var value = map.get(name)
    if (value eq null) {
      val gauge = ConcurrentMetricState.Gauge(name, "", Chunk(tags: _*), new AtomicReference(0.0))
      map.putIfAbsent(name, gauge)
      value = map.get(name)
    }
    value match {
      case gauge: ConcurrentMetricState.Gauge =>
        new Gauge {
          def set(value: Double): UIO[Any]    =
            ZIO.succeed(gauge.set(value))
          def adjust(value: Double): UIO[Any] =
            ZIO.succeed(gauge.adjust(value))
        }
      case _                                  => Gauge.none
    }
  }

  /**
   * Increase a named counter by some value.
   */
  def getCounter(name: String, tags: Label*): Counter = {
    var value = map.get(name)
    if (value eq null) {
      val counter = ConcurrentMetricState.Counter(name, "", Chunk(tags: _*), new DoubleAdder)
      map.putIfAbsent(name, counter)
      value = map.get(name)
    }
    value match {
      case counter: ConcurrentMetricState.Counter =>
        new Counter {
          def increment(value: Double): UIO[Any] = ZIO.succeed(counter.increment(value))
        }
      case _                                      => Counter.none
    }
  }

  /**
   * Observe a value and feed it into a histogram
   */
  def getDoubleHistogram(name: String, ht: HistogramType, tags: Label*): DoubleHistogram = {
    val _     = ht
    var value = map.get(name)
    if (value eq null) {
      val doubleHistogram = ConcurrentMetricState.DoubleHistogram(
        name,
        "",
        Chunk(tags: _*),
        ???,
        ???,
        new AtomicReferenceArray[Double](1),
        new LongAdder()
      )
      map.putIfAbsent(name, doubleHistogram)
      value = map.get(name)
    }
    value match {
      case doubleHistogram: ConcurrentMetricState.DoubleHistogram =>
        new DoubleHistogram {
          def observe(value: Double): UIO[Any] =
            ZIO.succeed(doubleHistogram.observe(value))
        }
      case _                                                      => DoubleHistogram.none
    }
  }

  /**
   * Record a String to track the number of different values within the given name.
   */
  def getStringHistogram(name: String, v: String, tags: Label*): Unit =
    ???

  def snapshot(): Map[String, MetricState] = {
    val iterator = map.entrySet.iterator
    val builder  = scala.collection.immutable.Map.newBuilder[String, MetricState]
    while (iterator.hasNext) {
      val entry = iterator.next()
      val key   = entry.getKey
      val value = entry.getValue
      builder += (key -> value.toMetricState)
    }
    builder.result()
  }
}
