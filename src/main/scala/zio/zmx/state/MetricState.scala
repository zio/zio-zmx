package zio.zmx.state

import zio._
import zio.zmx.Label
import zio.zmx.state.MetricType._

final case class MetricState(
  name: String,
  help: String,
  labels: Chunk[Label],
  details: MetricType
) {
  override def toString(): String = {
    val lbls = if (labels.isEmpty) "" else labels.map(l => s"${l._1}->${l._2}").mkString("{", ",", "}")
    s"MetricState($name$lbls, $details)"
  }
}

object MetricState {

  // --------- Methods creating and using Prometheus counters
  def counter(name: String, help: String, value: Double, labels: Chunk[Label] = Chunk.empty): MetricState =
    MetricState(name, help, labels, Counter(value))

  // --------- Methods creating and using Prometheus Gauges

  def gauge(
    name: String,
    help: String,
    startAt: Double,
    labels: Chunk[Label] = Chunk.empty
  ): MetricState =
    MetricState(name, help, labels, Gauge(startAt))

  // --------- Methods creating and using Prometheus Histograms

  def doubleHistogram(
    name: String,
    help: String,
    buckets: DoubleHistogramBuckets,
    sum: Double,
    labels: Chunk[Label] = Chunk.empty
  ): MetricState =
    MetricState(
      name,
      help,
      labels,
      DoubleHistogram(buckets.buckets, sum)
    )

  // --------- Methods creating and using Prometheus Histograms

  def summary(
    name: String,
    help: String,
    error: Double,
    quantiles: Chunk[(Double, Option[Double])],
    count: Long,
    sum: Double,
    labels: Chunk[Label]
  ): MetricState =
    MetricState(
      name,
      help,
      labels,
      Summary(error, quantiles, count, sum)
    )
}
