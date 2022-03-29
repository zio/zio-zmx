package zio.zmx.prometheus

import zio._

import zhttp.http._
import zhttp.service._

object PrometheusHttpApp {

  val app =
    Http
      .collectZIO[Request] { case Method.GET -> !! / "metrics" =>
        PrometheusClient.snapshot
          .map(Response.text(_))
      }

}
