package zio.zmx.example

import io.netty.handler.codec.http.{ HttpHeaderNames, HttpHeaderValues }

import zhttp.http._
import zhttp.http.Method.GET
import zhttp.service.Server
import zio._
import zio.console.{ getStrLn, putStrLn }
import zio.zmx.prometheus.PrometheusClient
import zio.zmx.statsd.StatsdClient

object ZmxSampleApp extends App with InstrumentedSample {

  private val port = 8080

  private val contentTypeHtml = Header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML)

  private lazy val httpApp = Http.collectM[Request] {

    case GET -> Root =>
      val html = htmlResponseOf(
        """<html>
          |<title>Simple Server</title>
          |<body>
          |<p><a href="/metrics">Metrics</a></p>
          |<p><a href="/json">Json</a></p>
          |</body
          |</html>""".stripMargin
      )
      ZIO.succeed(html)

    case GET -> Root / "metrics" =>
      PrometheusClient.snapshot.map { prom =>
        Response.text(prom.value)
      }

    case GET -> Root / "json"    =>
      StatsdClient.snapshot.map { json =>
        Response.jsonString(json.value)
      }
  }

  private lazy val execute =
    (for {
      s <- Server.start(port, httpApp).fork
      p <- program.fork
      _ <- putStrLn("Press Any Key") *> getStrLn.catchAll(_ => ZIO.none) *> s.interrupt *> p.interrupt
    } yield ExitCode.success).orDie

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    execute.provideCustomLayer(StatsdClient.default ++ PrometheusClient.live)

  private def htmlResponseOf(markup: String): UResponse =
    Response.http(
      headers = List(contentTypeHtml),
      content = HttpData.CompleteData(
        Chunk.fromArray(markup.getBytes(HTTP_CHARSET))
      )
    )
}
