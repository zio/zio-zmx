package zio.metrics.connectors

import zio._
import zio.metrics.connectors.internal.MetricsClient

import zhttp.http._

package object prometheus {

  lazy val prometheusLayer: ZLayer[MetricsConfig, Nothing, Unit] =
    ZLayer.scoped(
      PrometheusPublisher.make.map(clt => MetricsClient.make(prometheusHandler(clt))).unit,
    )

  val prometheusRouter =
    Http
      .collectZIO[Request] { case Method.GET -> !! / "metrics" =>
        ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))
      }

  private def prometheusHandler(clt: PrometheusPublisher): Iterable[MetricEvent] => UIO[Unit] = events =>
    for {
      reportComplete <- ZIO.foreach(events)(evt =>
                          for {
                            reportEvent <-
                              PrometheusEncoder.encode(evt).map(_.mkString("\n")).catchAll(_ => ZIO.succeed(""))
                          } yield reportEvent,
                        )
      _              <- clt.set(reportComplete.mkString("\n"))
    } yield ()

}
