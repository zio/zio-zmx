package zio.zmx.prometheus

import zio._
import zio.metrics._

import java.time.Instant

trait PrometheusClient {
  def snapshot: UIO[String]
}

object PrometheusClient {

  val live: Layer[Nothing, PrometheusClient] =
    ZLayer.succeed {
      new PrometheusClient {
        def snapshot: ZIO[Any, Nothing, String] =
          ZIO.succeed(PrometheusEncoder.encode(MetricClient.unsafeStates.values, Instant.now()))
      }
    }

  val snapshot: ZIO[PrometheusClient, Nothing, String] =
    ZIO.serviceWithZIO[PrometheusClient](_.snapshot)
}
