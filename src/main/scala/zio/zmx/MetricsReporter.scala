package zio.zmx

import zio._
import zio.clock._
import zio.stm._

import zio.zmx.prometheus.PrometheusConfig
import zio.zmx.statsd.StatsdConfig
import zio.zmx.prometheus.PrometheusReporter
import zio.zmx.prometheus.PrometheusRegistry
import zio.zmx.statsd.StatsdReporter
import zio.zmx.metrics.MetricsDataModel.MetricEvent

trait MetricsReporter {
  def report(event: MetricEvent): UIO[Any]
}

object MetricsReporter {

  def report(event: MetricEvent): ZIO[Has[MetricsReporter], Nothing, Any] =
    ZIO.accessM(_.get.report(event))

  val none: ZLayer[Any, Nothing, Any] =
    ZLayer.succeed {
      new MetricsReporter {
        def report(event: MetricEvent): UIO[Any] =
          ZIO.unit
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
      map    <- TMap.empty[String, Double].commit
    } yield StatsdReporter(config, map)).toLayer
}
