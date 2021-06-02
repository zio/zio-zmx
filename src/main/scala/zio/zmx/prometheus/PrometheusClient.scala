package zio.zmx.prometheus

import zio._

import java.time.Instant
import zio.zmx.MetricsClient
import zio.zmx.MetricSnapshot.Prometheus

trait PrometheusClient extends MetricsClient {
  def snapshot: ZIO[Any, Nothing, Prometheus]
}

object PrometheusClient {

  val live: ZLayer[Any, Nothing, Has[PrometheusClient]] =
    ZLayer.succeed {
      new PrometheusClient {
        def snapshot: ZIO[Any, Nothing, Prometheus] =
          ZIO.succeed(PrometheusEncoder.encode(zmx.internal.snapshot().values, Instant.now()))
      }
    }

  val snapshot: ZIO[Has[PrometheusClient], Nothing, Prometheus] =
    ZIO.serviceWith(_.snapshot)
}
