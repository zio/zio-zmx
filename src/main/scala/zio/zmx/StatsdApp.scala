package zio.zmx

import zio._
import zio.zmx.statsd.StatsdConfig

trait StatsdApp extends ZMXApp {

  def statsdConfig(args: List[String]): URIO[ZEnv, StatsdConfig] = {
    val _ = args
    UIO.succeed(StatsdConfig.default)
  }

  def metricsReporter(args: List[String]): ZLayer[ZEnv, Nothing, Has[MetricsReporter]] =
    statsdConfig(args).toLayer >>> MetricsReporter.statsd
}
