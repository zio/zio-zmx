package zio.zmx.statsd

final case class StatsdConfig(
  host: String,
  port: Int
)
