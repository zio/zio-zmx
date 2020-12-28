package zio.zmx.prometheus

import zio._
import zio.zmx.metrics.MetricsDataModel.MetricEvent
import zio.zmx.prometheus.PMetric.Buckets

final case class PrometheusConfig(
  // Provide a possibility to inject descriptions into the Prometheus Registry
  descriptions: Chunk[MetricEvent => Option[String]] = Chunk.empty,
  // Provide a possibility to inject Histogram buckets into the Prometheus Registry
  buckets: Chunk[MetricEvent => Option[Buckets]] = Chunk.empty
)
