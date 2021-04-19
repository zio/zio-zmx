package zio.zmx.internal

import java.time.Duration
import java.util.concurrent.atomic.{ AtomicReference, AtomicReferenceArray, DoubleAdder, LongAdder }
import java.util.concurrent.ConcurrentHashMap
import zio.zmx.Label
import zio.Chunk
import zio.internal.MutableConcurrentQueue
import zio.zmx.state.Quantile
import zio.zmx.state.{ DoubleHistogramBuckets, MetricState }
import scala.collection.JavaConverters._

sealed trait ConcurrentMetricState { self =>
  def name: String
  def help: String
  def labels: Chunk[Label]

  def toMetricState: MetricState =
    self match {
      case ConcurrentMetricState.Counter(name, help, labels, value)                                =>
        MetricState.counter(name, help, value.doubleValue, labels)
      case ConcurrentMetricState.Gauge(name, help, labels, value)                                  =>
        MetricState.gauge(name, help, value.get, labels)
      case ConcurrentMetricState.DoubleHistogram(name, help, labels, _, boundaries, values, count) =>
        val array         = atomicArraytoArray(values)
        val valuesChunk   = Chunk.fromArray(array)
        val combinedChunk = boundaries.zip(valuesChunk)
        MetricState.doubleHistogram(name, help, DoubleHistogramBuckets(combinedChunk), count.longValue, labels)
      case ConcurrentMetricState.Summary(name, help, labels, timeSeries, quantiles, maxAge)        =>
        val chunk = Chunk.fromIterable(timeSeries.pollUpTo(timeSeries.capacity)).map { timeStampedDouble =>
          (timeStampedDouble.value, java.time.Instant.ofEpochMilli(timeStampedDouble.timeStamp))
        }
        MetricState.summary(name, help, chunk, maxAge, timeSeries.capacity, labels)(quantiles: _*)
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

  final case class DoubleHistogram(
    name: String,
    help: String,
    labels: Chunk[Label],
    bucket: Double => Int,
    boundaries: Chunk[Double],
    values: AtomicReferenceArray[Double],
    count: LongAdder
  ) extends ConcurrentMetricState {
    def observe(value: Double): Unit =
      ???
  }

  final case class Summary(
    name: String,
    help: String,
    labels: Chunk[Label],
    timeSeries: MutableConcurrentQueue[TimeStampedDouble],
    quantiles: Array[Quantile],
    maxAge: Duration
  ) extends ConcurrentMetricState

  final case class TimeStampedDouble(value: Double, timeStamp: Long)
}
