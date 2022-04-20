package zio.zmx.statsd

import zio._

final case class StatsdConfig(
  host: String,
  port: Int,
  pollInterval: Duration)

object StatsdConfig {

  val default: StatsdConfig =
    StatsdConfig("localhost", 8125, 10.seconds)

  val defaultLayer = ZLayer.succeed(default)
}
