package zio.zmx.internal

import java.time.Duration
import java.util.concurrent.atomic.{ AtomicReference, DoubleAdder }
import zio.zmx.Label
import zio.Chunk
import zio.zmx.state.{ DoubleHistogramBuckets, MetricState }
import zio.zmx.MetricKey

sealed trait ConcurrentMetricState { self =>
  def name: String
  def help: String
  def labels: Chunk[Label]

  def toMetricState: MetricState =
    self match {
      case ConcurrentMetricState.Counter(key, help, value)                            =>
        MetricState.counter(key, help, value.doubleValue)
      case ConcurrentMetricState.Gauge(key, help, value)                              =>
        MetricState.gauge(key, help, value.get)
      case ConcurrentMetricState.Histogram(name, help, labels, histogram)             =>
        MetricState.doubleHistogram(name, help, DoubleHistogramBuckets(histogram.snapshot()), histogram.sum(), labels)
      case ConcurrentMetricState.Summary(name, help, labels, error, _, _, _, summary) =>
        MetricState.summary(
          name,
          help,
          error,
          summary.snapshot(java.time.Instant.now())._2,
          summary.count(),
          summary.sum(),
          labels
        )
    }
}

object ConcurrentMetricState {

  final case class Counter(key: MetricKey.Counter, help: String, value: DoubleAdder) extends ConcurrentMetricState {
    def increment(v: Double): Unit =
      value.add(v)
  }

  final case class Gauge(key: MetricKey.Gauge, help: String, value: AtomicReference[Double])
      extends ConcurrentMetricState {
    def set(v: Double): Unit =
      value.lazySet(v)
    def adjust(v: Double): Unit = {
      val _ = value.updateAndGet(_ + v)
    }
  }

  final case class Histogram(
    name: String,
    help: String,
    labels: Chunk[Label],
    histogram: ConcurrentHistogram
  ) extends ConcurrentMetricState {
    def observe(value: Double): Unit =
      histogram.observe(value)
  }

  final case class Summary(
    name: String,
    help: String,
    labels: Chunk[Label],
    error: Double,
    quantiles: Chunk[Double],
    maxAge: Duration,
    maxSize: Int,
    summary: ConcurrentSummary
  ) extends ConcurrentMetricState {
    def observe(value: Double, t: java.time.Instant): Unit =
      summary.observe(value, t)
  }

  final case class TimeStampedDouble(value: Double, timeStamp: java.time.Instant)
}
