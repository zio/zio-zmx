package zio.metrics

import zio._
import zio.metrics.connectors.statsd.StatsdConfig

package object connectors {

  type NewRelicConfig

  type MetricsConfig

  lazy val newRelic: ZLayer[NewRelicConfig & MetricsConfig, Nothing, Unit] = ???

  lazy val prometheusX: ZLayer[MetricsConfig, Nothing, Unit] = ???

  lazy val statsD: ZLayer[StatsdConfig & MetricsConfig, Nothing, Unit] = ???

  def newRelic(cfg: NewRelicConfig, metricsCfg: MetricsConfig): ZLayer[Any, Nothing, Unit] = ???

  def statsD(cfg: StatsdConfig, metricsCfg: MetricsConfig): ZLayer[Any, Nothing, Unit] = ???

  // Replacement for MetricClient
  def makeStreamingConnector[R: Tag](
    onUpdate: Iterable[MetricEvent] => Task[Unit],
  ): ZIO[R & Scope & MetricsConfig, Nothing, Unit] = ???

  // This is for Prometheus and the likes
  // This will be fed a complete set of metrics
  def makeBatchConnector[Report, R: Tag](
    onUpdate: Set[MetricPair.Untyped] => Task[Report],
  ): ZIO[R & Scope & MetricsConfig, Nothing, Ref[Report]] = ???

}
