package zio.metrics.connectors

import zhttp.http._
import zhttp.service.Server

import zio._

import zhttp.service.server.ServerChannelFactory
import zhttp.service.EventLoopGroup
import zhttp.html._
import zio.metrics.jvm.DefaultJvmMetrics
import zio.metrics.connectors.statsd.StatsdConfig
// import zhttp.service.ChannelFactory
// import zio.metrics.connectors.newrelic.NewRelicConfig

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

  private lazy val runHttp = (server.start *> ZIO.never).forkDaemon

  override def run: ZIO[Environment & ZIOAppArgs & Scope,Any,Any] = (for { 
    f <- runHttp
    _ <- program
    _ <- f.join
  } yield ())
    .provide(
      ServerChannelFactory.auto,
      EventLoopGroup.auto(nThreads),

      // This is the general config for all backends 
      metricsConfig,

      // The prometheus reporting layer
      prometheus.publisherLayer,
      prometheus.prometheusLayer, 

      // The statsd reporting layer
      ZLayer.succeed(StatsdConfig("127.0.0.1", 8125)),
      statsd.statsdLayer,

      // The NewRelic reporting layer
      // ChannelFactory.nio,
      // NewRelicConfig.fromEnvEULayer,
      // newrelic.newRelicLayer,

      // Enable the ZIO internal metrics and the default JVM metricsConfig
      // Do NOT forget the .unit for the JVM metrics layer
      Runtime.trackRuntimeMetrics,
      DefaultJvmMetrics.live.unit
    )

}

