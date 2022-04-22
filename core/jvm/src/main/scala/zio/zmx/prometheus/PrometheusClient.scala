package zio.zmx.prometheus

import java.time.Instant

import zio._

trait PrometheusClient {
  def snapshot: UIO[String]
}

object PrometheusClient {

  val live: ZLayer[Any, Nothing, PrometheusClient] =
    ZLayer.succeed(
      new PrometheusClient {
        def snapshot: UIO[String] =
          ZIO.succeed {
            val current = zio.internal.metrics.metricRegistry.snapshot()
            PrometheusEncoder.encode(current, Instant.now())
          }
      },
    )

  val snapshot: ZIO[PrometheusClient, Nothing, String] =
    ZIO.serviceWithZIO[PrometheusClient](_.snapshot)
}
