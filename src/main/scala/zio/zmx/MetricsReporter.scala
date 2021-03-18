package zio.zmx

import zio._
import zio.clock._

import zio.zmx.prometheus.PrometheusConfig
import zio.zmx.statsd.StatsdConfig
import zio.zmx.prometheus.PrometheusReporter
import zio.zmx.prometheus.PrometheusRegistry
import zio.zmx.statsd.StatsdReporter
import zio.zmx.metrics.MetricEvent
import zio.zmx.state.MetricState

trait MetricsReporter {
  def report(event: MetricEvent): UIO[Any]
  def snapshot: UIO[Map[String, MetricState]]
}

object MetricsReporter {

  def report(event: MetricEvent): ZIO[Has[MetricsReporter], Nothing, Any] =
    ZIO.accessM(_.get.report(event))

  val none: ZLayer[Any, Nothing, Any] =
    ZLayer.succeed {
      new MetricsReporter {
        def report(event: MetricEvent): UIO[Any]    =
          ZIO.unit
        val snapshot: UIO[Map[String, MetricState]] =
          UIO.succeedNow(Map.empty)
      }
    }

  val prometheus: ZLayer[Has[PrometheusConfig] with Has[Clock.Service], Nothing, Has[MetricsReporter]] =
    (for {
      config   <- ZIO.service[PrometheusConfig]
      registry <- PrometheusRegistry.make(config)
    } yield PrometheusReporter(registry)).toLayer

  val statsd: ZLayer[Has[StatsdConfig], Nothing, Has[MetricsReporter]] =
    (for {
      config <- ZIO.service[StatsdConfig]
      gauges <- Ref.make[Map[String, Double]](Map.empty)
    } yield StatsdReporter(config, gauges)).toLayer
}
