package zio.zmx.internal

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{ AtomicReference, DoubleAdder }

import zio._
import zio.zmx._
import zio.zmx.metrics._
import zio.zmx.state.MetricState
import java.time.Duration

class ConcurrentState {

  private val listeners = zio.internal.Platform.newConcurrentSet[MetricListener]()

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
      def setGauge(name: String, value: Double, tags: Label*): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.setGauge(name, value, tags: _*)
        }
      }
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
      def observeSummary(
        name: String,
        maxAge: Duration,
        maxSize: Int,
        error: Double,
        quantiles: Chunk[Double],
        tags: Label*
      ): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.observeSummary(name, maxAge, maxSize, error, quantiles, tags: _*)
        }
      }
      def observeString(name: String, value: String, setTag: String, tags: Label*): Unit = {
        val iterator = listeners.iterator()
        while (iterator.hasNext()) {
          val listener = iterator.next()
          listener.observeString(name, value, setTag, tags: _*)
        }
      }
    }

  val map: ConcurrentHashMap[MetricKey, ConcurrentMetricState] =
    new ConcurrentHashMap[MetricKey, ConcurrentMetricState]()

  /**
   * Increase a named counter by some value.
   */
  def getCounter(key: MetricKey.Counter): Counter = {
    var value = map.get(key)
    if (value eq null) {
      val counter = ConcurrentMetricState.Counter(key, "", new DoubleAdder)
      map.putIfAbsent(key, counter)
      value = map.get(key)
    }
    value match {
      case counter: ConcurrentMetricState.Counter =>
        new Counter {
          override def increment(value: Double): UIO[Any] =
            ZIO.succeed {
              listener.incrementCounter(key.name, value, key.tags: _*)
              counter.increment(value)
            }
        }
      case _                                      => Counter.none
    }
  }

  def getGauge(key: MetricKey.Gauge): Gauge = {
    var value = map.get(key)
    if (value eq null) {
      val gauge = ConcurrentMetricState.Gauge(key, "", new AtomicReference(0.0))
      map.putIfAbsent(key, gauge)
      value = map.get(key)
    }
    value match {
      case gauge: ConcurrentMetricState.Gauge =>
        new Gauge {
          def set(value: Double): UIO[Any]    =
            ZIO.succeed {
              listener.setGauge(key.name, value, key.tags: _*)
              gauge.set(value)
            }
          def adjust(value: Double): UIO[Any] =
            ZIO.succeed {
              listener.adjustGauge(key.name, value, key.tags: _*)
              gauge.adjust(value)
            }
        }
      case _                                  => Gauge.none
    }
  }

  /**
   * Observe a value and feed it into a histogram
   */
  def getHistogram(key: MetricKey.Histogram): Histogram = {
    var value = map.get(key)
    if (value eq null) {
      val histogram = ConcurrentMetricState.Histogram(
        key,
        "",
        ConcurrentHistogram.manual(key.boundaries)
      )
      map.putIfAbsent(key, histogram)
      value = map.get(key)
    }
    value match {
      case histogram: ConcurrentMetricState.Histogram =>
        new Histogram {
          def observe(value: Double): UIO[Any] =
            ZIO.succeed {
              listener.observeHistogram(key.name, key.boundaries, key.tags: _*)
              histogram.observe(value)
            }
        }
      case _                                          => Histogram.none
    }
  }

  def getSummary(
    key: MetricKey.Summary
  ): Summary = {
    var value = map.get(key)
    if (value eq null) {
      val summary = ConcurrentMetricState.Summary(
        key,
        "",
        ConcurrentSummary.manual(key.maxSize, key.maxAge, key.error, key.quantiles)
      )
      map.putIfAbsent(key, summary)
      value = map.get(key)
    }
    value match {
      case summary: ConcurrentMetricState.Summary =>
        new Summary {
          def observe(value: Double, t: java.time.Instant): UIO[Any] =
            ZIO.succeed {
              listener.observeSummary(key.name, key.maxAge, key.maxSize, key.error, key.quantiles, key.tags: _*)
              summary.observe(value, t)
            }
        }
      case _                                      => Summary.none
    }
  }

  def getSetCount(key: MetricKey.SetCount): SetCount = {
    var value = map.get(key)
    if (value eq null) {
      val setCount = ConcurrentMetricState.SetCount(
        key,
        "",
        ConcurrentSetCount.manual()
      )
      map.putIfAbsent(key, setCount)
      value = map.get(key)
    }
    value match {
      case setCount: ConcurrentMetricState.SetCount =>
        new SetCount {
          def observe(word: String) = ZIO.succeed {
            listener.observeString(key.name, word, key.setTag, key.tags: _*)
            setCount.observe(word)
          }
        }
      case _                                        => SetCount.none
    }
  }

  def snapshot(): Map[MetricKey, MetricState] = {
    val iterator = map.values.iterator
    val builder  = scala.collection.immutable.Map.newBuilder[MetricKey, MetricState]
    while (iterator.hasNext) {
      val value  = iterator.next()
      val states = value.toMetricStates
      states.foreach { case (k, s) => builder.addOne((k, s)) }
    }
    builder.result()
  }
}
