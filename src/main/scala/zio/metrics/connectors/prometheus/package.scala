package zio.metrics.connectors

import zio._
import zio.metrics.connectors.internal.MetricsClient

package object prometheus {

  lazy val publisherLayer: ULayer[PrometheusPublisher] = ZLayer.fromZIO(PrometheusPublisher.make)

  lazy val prometheusLayer: ZLayer[MetricsConfig & PrometheusPublisher, Nothing, Unit] =
    ZLayer.fromZIO(
      ZIO.service[PrometheusPublisher].flatMap(clt => MetricsClient.make(prometheusHandler(clt))).unit,
    )

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
