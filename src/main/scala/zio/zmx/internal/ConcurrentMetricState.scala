package zio.zmx.internal

import java.time.Duration
import java.util.concurrent.atomic.{ AtomicReference, DoubleAdder }
import zio.zmx.Label
import zio.Chunk
import zio.internal.MutableConcurrentQueue
import zio.zmx.state.{ DoubleHistogramBuckets, MetricState }

sealed trait ConcurrentMetricState { self =>
  def name: String
  def help: String
  def labels: Chunk[Label]

  def toMetricState: MetricState =
    self match {
      case ConcurrentMetricState.Counter(name, help, labels, value)                 =>
        MetricState.counter(name, help, value.doubleValue, labels)
      case ConcurrentMetricState.Gauge(name, help, labels, value)                   =>
        MetricState.gauge(name, help, value.get, labels)
      case ConcurrentMetricState.Histogram(name, help, labels, histogram)           =>
        MetricState.doubleHistogram(name, help, DoubleHistogramBuckets(histogram.snapshot()), histogram.count(), labels)
      case ConcurrentMetricState.Summary(name, help, labels, timeSeries, _, maxAge) =>
        val chunk = Chunk.fromIterable(timeSeries.pollUpTo(timeSeries.capacity)).map { timeStampedDouble =>
          (timeStampedDouble.value, java.time.Instant.ofEpochMilli(timeStampedDouble.timeStamp))
        }
        MetricState.summary(name, help, chunk, maxAge, timeSeries.capacity, labels)(???)
    }
}

object ConcurrentMetricState {

  final case class Counter(name: String, help: String, labels: Chunk[Label], value: DoubleAdder)
      extends ConcurrentMetricState {
    def increment(v: Double): Unit =
      value.add(v)
  }

  final case class Gauge(name: String, help: String, labels: Chunk[Label], value: AtomicReference[Double])
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
    timeSeries: MutableConcurrentQueue[TimeStampedDouble],
    quantiles: Chunk[Double],
    maxAge: Duration
  ) extends ConcurrentMetricState {
    def observe(value: Double): Unit =
      ???
  }

  final case class TimeStampedDouble(value: Double, timeStamp: Long)
}
