package zio.zmx.state

import zio.Chunk

sealed trait MetricType

object MetricType {

  final case class Counter(count: Double) extends MetricType

  final case class Gauge(value: Double) extends MetricType

  final case class DoubleHistogram(
    buckets: Chunk[(Double, Double)],
    count: Long
  ) extends MetricType {
    def sum: Double =
      buckets.map(_._2).sum
  }

  final case class Summary(
    samples: TimeSeries,
    quantiles: Chunk[Quantile],
    count: Long
  ) extends MetricType {
    def sum: Double =
      samples.samples.map(_._1).sum
  }
}
