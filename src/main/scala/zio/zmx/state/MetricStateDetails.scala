package zio.zmx.state

import zio.Chunk
import zio.zmx.Label

sealed trait MetricStateDetails

object MetricStateDetails {

  final case class Counter(count: Double) extends MetricStateDetails

  final case class Gauge(value: Double) extends MetricStateDetails

  final case class Histogram(
    buckets: Chunk[(Double, Double)],
    count: Double,
    sum: Double
  ) extends MetricStateDetails

  final case class Summary(
    samples: TimeSeries,
    quantiles: Chunk[Quantile],
    count: Double,
    sum: Double
  ) extends MetricStateDetails

  // --------- Methods creating and using Prometheus counters
  def counter(name: String, help: String, labels: Chunk[Label] = Chunk.empty): MetricState =
    MetricState(name, help, labels, Counter(0))

  // --------- Methods creating and using Prometheus Gauges

  def gauge(
    name: String,
    help: String,
    labels: Chunk[Label] = Chunk.empty,
    startAt: Double = 0.0
  ): MetricState =
    MetricState(name, help, labels, Gauge(startAt))

  // --------- Methods creating and using Prometheus Histograms

  def histogram(
    name: String,
    help: String,
    labels: Chunk[Label] = Chunk.empty,
    buckets: HistogramBuckets
  ): Option[MetricState] =
    if (labels.find(_._1.equals("le")).isDefined) None
    else
      Some(
        MetricState(
          name,
          help,
          labels,
          Histogram(buckets.buckets, 0, 0)
        )
      )

  // --------- Methods creating and using Prometheus Histograms

  def summary(
    name: String,
    help: String,
    labels: Chunk[Label],
    maxAge: java.time.Duration = TimeSeries.defaultMaxAge,
    maxSize: Int = TimeSeries.defaultMaxSize,
    samples: Chunk[(Double, java.time.Instant)] = Chunk.empty
  )(
    quantiles: Quantile*
  ): MetricState =
    MetricState(
      name,
      help,
      labels,
      Summary(TimeSeries(maxAge, maxSize, samples), Chunk.fromIterable(quantiles), 0, 0)
    )
}
