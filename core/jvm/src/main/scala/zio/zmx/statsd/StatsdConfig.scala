package zio.zmx.statsd

import zio.ZLayer

final case class StatsdConfig(
  host: String,
  port: Int)

object StatsdConfig {

  val default: StatsdConfig =
    StatsdConfig("localhost", 8125)

  val defaultLayer = ZLayer.succeed(default)
}
