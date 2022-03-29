package zio.zmx.prometheus

import zhttp.http._

object PrometheusHttpApp {

  val app =
    Http
      .collectZIO[Request] { case Method.GET -> !! / "metrics" =>
        PrometheusClient.snapshot
          .map(Response.text(_))
      }

}
