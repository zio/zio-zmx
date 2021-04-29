package zio.zmx.internal

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{ AtomicReference, AtomicReferenceArray, DoubleAdder, LongAdder }

import zio._
import zio.zmx._
import zio.zmx.metrics._
import zio.zmx.state.MetricState
import java.time.Duration

class ConcurrentState {

  private val listeners = zio.internal.Platform.newConcurrentSet[MetricListener]

  def installListener(listener: MetricListener): Unit = {
    listeners.add(listener)
    ()
  }

  def removeListener(listener: MetricListener): Unit = {
    listeners.remove(listener)
    ()
  }

  private val listener: MetricListener =
    new MetricListener {
      def adjustGauge(name: String, value: Double, tags: Label*): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.adjustGauge(name, value, tags: _*)
        }
      }
      def incrementCounter(name: String, value: Double, tags: Label*): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.incrementCounter(name, value, tags: _*)
        }
      }
      def observeHistogram(name: String, boundaries: Chunk[Double], tags: Label*): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.observeHistogram(name, boundaries, tags: _*)
        }
      }
      def observeSummary(name: String, maxAge: Duration, maxSize: Int, quantiles: Chunk[Double], tags: Label*): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.observeSummary(name, maxAge, maxSize, quantiles, tags: _*)
        }
      }
      def setGauge(name: String, value: Double, tags: Label*): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.setGauge(name, value, tags: _*)
        }
      }
    }

  val map: ConcurrentHashMap[MetricKey, ConcurrentMetricState] =
    new ConcurrentHashMap[MetricKey, ConcurrentMetricState]()

  def getGauge(name: String, tags: Label*): Gauge = {
    var value = map.get(name)
    if (value eq null) {
      val gauge = ConcurrentMetricState.Gauge(name, "", Chunk(tags: _*), new AtomicReference(0.0))
      map.putIfAbsent(MetricKey.Gauge(name, tags: _*), gauge)
      value = map.get(name)
    }
    value match {
      case gauge: ConcurrentMetricState.Gauge =>
        new Gauge {
          def set(value: Double): UIO[Any]    =
            ZIO.succeed {
              listener.setGauge(name, value, tags: _*)
              gauge.set(value)
            }
          def adjust(value: Double): UIO[Any] =
            ZIO.succeed {
              listener.adjustGauge(name, value, tags: _*)
              gauge.adjust(value)
            }
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
      map.putIfAbsent(MetricKey.Counter(name, tags: _*), counter)
      value = map.get(name)
    }
    value match {
      case counter: ConcurrentMetricState.Counter =>
        new Counter {
          def increment(value: Double): UIO[Any] =
            ZIO.succeed {
              listener.incrementCounter(name, value, tags: _*)
              counter.increment(value)
            }
        }
      case _                                      => Counter.none
    }
  }

  /**
   * Observe a value and feed it into a histogram
   */
  def getHistogram(name: String, boundaries: Chunk[Double], tags: Label*): Histogram = {
    var value = map.get(name)
    if (value eq null) {
      val doubleHistogram = ConcurrentMetricState.DoubleHistogram(
        name,
        "",
        Chunk(tags: _*),
        ???,
        boundaries,
        new AtomicReferenceArray[Double](1),
        new LongAdder()
      )
      map.putIfAbsent(MetricKey.Histogram(name, boundaries, tags: _*), doubleHistogram)
      value = map.get(name)
    }
    value match {
      case doubleHistogram: ConcurrentMetricState.DoubleHistogram =>
        new Histogram {
          def observe(value: Double): UIO[Any] =
            ZIO.succeed {
              listener.observeHistogram(name, boundaries, tags: _*)
              doubleHistogram.observe(value)
            }
        }
      case _                                                      => Histogram.none
    }
  }

  def getSummary(name: String, maxAge: Duration, maxSize: Int, quantiles: Chunk[Double], tags: Label*): Summary =
    ???

  def snapshot(): Map[MetricKey, MetricState] = {
    val iterator = map.entrySet.iterator
    val builder  = scala.collection.immutable.Map.newBuilder[MetricKey, MetricState]
    while (iterator.hasNext) {
      val entry = iterator.next()
      val key   = entry.getKey
      val value = entry.getValue
      builder += (key -> value.toMetricState)
    }
    builder.result()
  }
}
