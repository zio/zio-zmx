package zio.metrics.connectors.statsd

import zio._
import zio.ULayer

final case class StatsdConfig(
  host: String,
  port: Int)

object StatsdConfig {

  val default: StatsdConfig =
    StatsdConfig("localhost", 8125)

  val defaultLayer: ULayer[StatsdConfig] = ZLayer.succeed(default)
}
