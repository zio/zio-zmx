package zio.zmx.example

import java.net.InetSocketAddress

import uzhttp._
import uzhttp.server.Server

import zio._
import zio.console._
import zio.zmx._
import zio.zmx.statsd.StatsdClient
import zio.zmx.prometheus.PrometheusClient

object ZmxSampleApp extends App with InstrumentedSample {

  private val bindHost = "0.0.0.0"
  private val bindPort = 8080

  object path {
    def unapply(req: Request): Option[String] =
      Some(req.uri.getPath)
  }

  private lazy val server = Server
    .builder(new InetSocketAddress(bindHost, bindPort))
    .handleSome {
      case path("/")        =>
        ZIO.succeed(
          Response.html(
            """<html>
              |<title>Simple Server</title>
              |<body>
              |<p><a href="/metrics">Metrics</a></p>
              |<p><a href="/json">Json</a></p>
              |</body
              |</html>""".stripMargin
          )
        )
      case path("/metrics") =>
        ZIO
          .service[PrometheusClient]
          .flatMap(_.snapshot)
          .map(_.asInstanceOf[MetricSnapshot.Prometheus].value)
          .map(Response.plain(_))
      //  case path("/json")    =>
      //    val content = JsonEncoder.encode(snapshot().values)
      //    ZIO.succeed(Response.plain(content.value, headers = List("Content-Type" -> "application/json")))
    }
    .serve
    .use(s => s.awaitShutdown)

  private lazy val execute: ZIO[ZEnv with Has[PrometheusClient], Nothing, ExitCode] =
    (for {
      s <- server.fork
      _ <- putStrLn("Press Any Key")
      p <- program.fork
      f <- getStrLn.fork
      _ <- f.join *> s.interrupt *> p.interrupt
    } yield ExitCode.success).catchAll(_ => ZIO.succeed(ExitCode.failure))

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    execute.provideCustomLayer(PrometheusClient.live ++ StatsdClient.default)
}
