package zio.zmx.internal

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{ AtomicReference, DoubleAdder }

import zio._
import zio.zmx.metrics._
import zio.zmx.state.MetricState

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
      def setGauge(key: MetricKey.Gauge, value: Double): UIO[Unit] =
        ZIO.loop_(listeners.iterator())(_.hasNext(), i => i)(_.next().setGauge(key, value))

      def setCounter(key: MetricKey.Counter, value: Double): UIO[Unit] =
        ZIO.loop_(listeners.iterator())(_.hasNext(), i => i)(_.next().setCounter(key, value))

      def observeHistogram(key: MetricKey.Histogram, value: Double): UIO[Unit] =
        ZIO.loop_(listeners.iterator())(_.hasNext(), i => i)(_.next().observeHistogram(key, value))

      def observeSummary(key: MetricKey.Summary, value: Double): UIO[Unit] =
        ZIO.loop_(listeners.iterator())(_.hasNext(), i => i)(_.next().observeSummary(key, value))
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
          override def increment(value: Double): UIO[Unit] =
            listener.setCounter(key, counter.increment(value))
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
          def set(value: Double): UIO[Unit]    =
            listener.setGauge(key, gauge.set(value))
          def adjust(value: Double): UIO[Unit] =
            listener.setGauge(key, gauge.adjust(value))
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
          def observe(value: Double): UIO[Unit] =
            listener.observeHistogram(key, histogram.observe(value))
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
          def observe(value: Double, t: java.time.Instant): UIO[Unit] =
            listener.observeSummary(key, summary.observe(value, t))
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
          def observe(word: String): UIO[Unit] =
            listener.setCounter(key.counterKey(word), setCount.observe(word))

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
