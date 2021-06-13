package zio.zmx.example

import java.net.InetSocketAddress

import uzhttp._
import uzhttp.server.Server

import zio._
import zio.console._
import zio.zmx.MetricSnapshot.{ Json, Prometheus }
import zio.zmx.prometheus.PrometheusClient
import zio.zmx.statsd.StatsdClient

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
        PrometheusClient.snapshot.map { case Prometheus(value) =>
          Response.plain(value)
        }
      case path("/json")    =>
        StatsdClient.snapshot.map { case Json(value) =>
          Response.plain(value, headers = List("Content-Type" -> "application/json"))
        }
    }
    .serve
    .use(s => s.awaitShutdown)

  private lazy val execute =
    (for {
      s <- server.fork
      p <- program.fork
      _ <- putStrLn("Press Any Key") *> getStrLn.catchAll(_ => ZIO.none) *> s.interrupt *> p.interrupt
    } yield ExitCode.success).orDie

  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    execute.provideCustomLayer(StatsdClient.default ++ PrometheusClient.live)
}
