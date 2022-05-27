package zio.metrics.connectors

import zhttp.http._
import zhttp.service.Server

import zio._

import zhttp.service.server.ServerChannelFactory
import zhttp.service.EventLoopGroup
import zhttp.html._

object ZmxSampleApp extends ZIOAppDefault with InstrumentedSample {

  private val bindPort = 8080
  private val nThreads = 5

  private val metricsConfig = ZLayer.succeed(MetricsConfig(5.seconds))

  private lazy val indexPage =
     """<html>
       |<title>Simple Server</title>
       |<body>
       |<p><a href="/metrics">Metrics</a></p>
       |</body
       |</html>""".stripMargin

  private lazy val static     =
    Http.collect[Request] { case Method.GET -> !! => Response.html(Html.fromString(indexPage)) }

  private val server = Server.port(bindPort) ++ Server.app(static ++ prometheus.prometheusRouter)

  private lazy val execute =
    for {
      s <- (server.start *> ZIO.never).forkDaemon
      _ <- s.join
    } yield ()

  override def run: ZIO[Environment & ZIOAppArgs & Scope,Any,Any] = (for { 
    _ <- program
    _ <- execute
  } yield ())
    .provide(
      ServerChannelFactory.auto,
      EventLoopGroup.auto(nThreads),
      metricsConfig,
      prometheus.publisherLayer,
      prometheus.prometheusLayer
    )

}

