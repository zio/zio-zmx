package zio.zmx

import zio.duration.Duration

trait MetricsConfigDataModel {
  sealed case class MetricsConfig(
    bufferSize: Int,
    timeout: Duration,
    port: Option[Int],
    host: Option[String]
  )
}
