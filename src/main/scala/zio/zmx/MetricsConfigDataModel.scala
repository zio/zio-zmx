package zio.zmx

import zio.duration.Duration

trait MetricsConfigDataModel {
  sealed case class MetricsConfig(
    maximumSize: Int,
    bufferSize: Int,
    timeout: Duration,
    pollRate: Duration,
    host: Option[String],
    port: Option[Int]
  )
}
