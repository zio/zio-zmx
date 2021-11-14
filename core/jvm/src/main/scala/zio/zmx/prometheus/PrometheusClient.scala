package zio.zmx.prometheus

import zio._
import zio.metrics._

import java.time.Instant

trait PrometheusClient {
  def snapshot: ZIO[Any, Nothing, String]
}

object PrometheusClient {

  val live: ZServiceBuilder[Any, Nothing, Has[PrometheusClient]] =
    ZServiceBuilder.succeed {
      new PrometheusClient {
        def snapshot: ZIO[Any, Nothing, String] =
          ZIO.succeed(PrometheusEncoder.encode(MetricClient.unsafeSnapshot.values, Instant.now()))
      }
    }

  val snapshot: ZIO[Has[PrometheusClient], Nothing, String] =
    ZIO.serviceWith(_.snapshot)
}
