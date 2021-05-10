package zio.zmx

import zio._
import zio.clock._

trait PrometheusApp extends ZMXApp {

  def prometheusConfig(args: List[String]): URIO[ZEnv, PrometheusConfig] = {
    val _ = args
    UIO.succeed(PrometheusConfig.default)
  }

  def metricsReporter(args: List[String]): ZLayer[ZEnv, Nothing, Has[MetricsReporter]] =
    (prometheusConfig(args).toLayer ++ Clock.any) >>> MetricsReporter.prometheus
}
