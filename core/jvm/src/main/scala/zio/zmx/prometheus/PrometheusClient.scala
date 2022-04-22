package zio.zmx.prometheus

import java.time.Instant

import zio._
import zio.metrics._
import zio.zmx._

trait PrometheusClient {
  def snapshot: UIO[String]
}

object PrometheusClient {

  val live: Layer[MetricClient, PrometheusClient] =
    ZLayer.fromZIO(
      ZIO.serviceWith[MetricClient].map { clt =>
        new PrometheusClient {
          def snapshot: ZIO[Any, Nothing, String] = clt.snapshot()
        }
      },
    )

  val snapshot: ZIO[PrometheusClient, Nothing, String] =
    ZIO.serviceWithZIO[PrometheusClient](_.snapshot)
}
