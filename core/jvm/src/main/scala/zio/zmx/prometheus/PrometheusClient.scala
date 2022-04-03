package zio.zmx.prometheus

import java.time.Instant

import zio._
import zio.metrics._

trait PrometheusClient {
  def snapshot: UIO[String]
}

object PrometheusClient {

  val live: Layer[Nothing, PrometheusClient] =
    ZLayer.succeed {
      new PrometheusClient {
        def snapshot: ZIO[Any, Nothing, String] =
          ZIO.succeed(PrometheusEncoder.encode(MetricClient.unsafeSnapshot(), Instant.now()))
      }
    }

  val snapshot: ZIO[PrometheusClient, Nothing, String] =
    ZIO.serviceWithZIO[PrometheusClient](_.snapshot)
}
