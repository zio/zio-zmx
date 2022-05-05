package zio.metrics.connectors.prometheus

import zhttp.http._

object PrometheusHttpApp {

  val app =
    Http
      .collectZIO[Request] { case Method.GET -> !! / "metrics" =>
        PrometheusPublisher.get.map(Response.text)
      }

}
