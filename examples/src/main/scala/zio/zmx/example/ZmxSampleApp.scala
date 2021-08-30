package zio.zmx.example

import zhttp.http._
import zhttp.service.Server

import zio._
import zio.console._
import zio.zmx.MetricSnapshot.{ Json, Prometheus }
import zio.zmx.statsd.StatsdClient
import zio.zmx.prometheus.PrometheusClient
import zhttp.service.server.ServerChannelFactory
import zhttp.service.EventLoopGroup

object ZmxSampleApp extends App with InstrumentedSample {

  private val bindPort = 8080
  private val nThreads = 5

  private lazy val indexPage = HttpData.CompleteData(
    Chunk
      .fromArray("""<html>
                   |<title>Simple Server</title>
                   |<body>
                   |<p><a href="/metrics">Metrics</a></p>
                   |<p><a href="/json">Json</a></p>
                   |</body
                   |</html>""".stripMargin.getBytes)
  )

  private lazy val static     =
    Http.collect[Request] { case Method.GET -> Root => Response.http[Any, Nothing](content = indexPage) }

  private lazy val httpEffect = Http.collectM[Request] {
    case Method.GET -> Root / "metrics" =>
      PrometheusClient.snapshot.map { case Prometheus(value) => Response.text(value) }
    case Method.GET -> Root / "json"    =>
      StatsdClient.snapshot.map { case Json(value) => Response.jsonString(value) }
  }

  private lazy val execute =
    (for {
      s <- ((Server.port(bindPort) ++ Server.app(static +++ httpEffect)).start *> ZIO.never).forkDaemon
      p <- program.fork
      _ <- putStrLn("Press Any Key to stop the demo server") *> getStrLn.catchAll(_ =>
             ZIO.none
           ) *> p.interrupt *> s.interrupt
    } yield ExitCode.success).orDie

  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    execute.provideCustomLayer(
      StatsdClient.default ++ PrometheusClient.live ++ ServerChannelFactory.auto ++ EventLoopGroup.auto(nThreads)
    )
}
