package zio.zmx.internal

import java.util.concurrent.atomic.{ AtomicReference, DoubleAdder, LongAdder }
import java.util.concurrent.atomic.AtomicReferenceArray
import java.util.concurrent.ConcurrentHashMap
import zio.zmx.Label
import zio.Chunk
import zio.internal.MutableConcurrentQueue
import zio.zmx.state.Quantile

sealed trait ConcurrentMetricState {
  def name: String
  def help: String
  def labels: Chunk[Label]
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
    buckets: AtomicReferenceArray[Double],
    sum: DoubleAdder,
    count: LongAdder
  ) extends ConcurrentMetricState

  final case class StringHistogram(
    name: String,
    help: String,
    labels: Chunk[Label],
    buckets: ConcurrentHashMap[String, LongAdder],
    count: LongAdder
  ) extends ConcurrentMetricState

  final case class Summary(
    name: String,
    help: String,
    labels: Chunk[Label],
    timeSeries: MutableConcurrentQueue[Double],
    quantiles: Array[Quantile]
  ) extends ConcurrentMetricState
}
