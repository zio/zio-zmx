package zio.zmx.client.backend

import zio._
import zio.metrics.jvm.DefaultJvmMetrics
import zio.zmx.notify.MetricNotifier
import zio.zmx.prometheus.{PrometheusClient, PrometheusHttpApp}

import zhttp.http._
import zhttp.service._

object MetricsServer extends ZIOAppDefault {

  private val portNumber      =
    8080
  private val stopServerAfter =
    8.hours

  private val httpApp =
    Http.collectZIO[Request] {
      case Method.GET -> !!        =>
        ZIO.succeed(Response.text("Welcome to the ZIO-ZMX 2.0 client"))
      case Method.GET -> !! / "ws" =>
        WebsocketHandler.socketApp.flatMap(_.toResponse)
    } ++ PrometheusHttpApp.app

  private val runSample =
    for {
      _ <- InstrumentedSample.program.fork
      _ <- ZIO.logInfo(s"Started sample instrumented metrics")
      s <- Server
             .start(portNumber, httpApp)
             .forkDaemon
      _ <- ZIO.logInfo(s"Started HTTP server on port <$portNumber>")
      f <- ZIO.unit.schedule(Schedule.duration(stopServerAfter)).fork
      _ <- f.join *> s.interrupt
    } yield ()

  def run = {
    val trackingFlags = RuntimeConfig.default.flags + RuntimeConfigFlag.TrackRuntimeMetrics
    ZIO
      .withRuntimeConfig(RuntimeConfig.default.copy(flags = trackingFlags))(runSample)
      .provide(
        Clock.live,
        Console.live,
        System.live,
        Random.live,
        WebsocketHandler.live,
        MetricNotifier.live,
        PrometheusClient.live,
      )
  }
}

object MetricsServerWithJVMMetrics extends ZIOApp.Proxy(MetricsServer <> DefaultJvmMetrics.app)
