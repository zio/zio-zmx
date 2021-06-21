package zio.zmx.statsd

final case class StatsdConfig(
  host: String,
  port: Int
)

object StatsdConfig {

  val default: StatsdConfig =
    StatsdConfig("localhost", 8125)
}
