package zio.zmx.prometheus

import zio._
import zio.zmx.metrics.MetricEvent
import zio.zmx.prometheus.PMetric.Buckets

final case class PrometheusConfig(
  // Provide a possibility to inject descriptions into the Prometheus Registry
  descriptions: Chunk[MetricEvent => Option[String]] = Chunk.empty,
  // Provide a possibility to inject Histogram buckets into the Prometheus Registry
  buckets: Chunk[MetricEvent => Option[Buckets]] = Chunk.empty,
  // Provide a possibility to inject Quantiles for Prometheus summaries into the Prometheus Registry
  quantiles: Seq[MetricEvent => Option[Seq[Quantile]]] = Chunk.empty
)

object PrometheusConfig {

  val default: PrometheusConfig =
    PrometheusConfig(Chunk.empty, Chunk.empty, Chunk.empty)

  lazy val defaultQuantiles: Seq[Quantile] = Seq(
    Quantile(0.5, 0.03),
    Quantile(0.95, 0.03)
  ).collect { case Some(q) =>
    q
  }
}
