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
    count: Long,
    labels: Chunk[Label] = Chunk.empty
  ): MetricState =
    MetricState(
      name,
      help,
      labels,
      DoubleHistogram(buckets.buckets, count)
    )

  // --------- Methods creating and using Prometheus Histograms

  def summary(
    name: String,
    help: String,
    samples: Chunk[(Double, java.time.Instant)],
    maxAge: java.time.Duration = TimeSeries.defaultMaxAge,
    maxSize: Int = TimeSeries.defaultMaxSize,
    labels: Chunk[Label]
  )(
    quantiles: Quantile*
  ): MetricState =
    MetricState(
      name,
      help,
      labels,
      Summary(TimeSeries(maxAge, maxSize, samples), Chunk.fromIterable(quantiles), 0)
    )
}
