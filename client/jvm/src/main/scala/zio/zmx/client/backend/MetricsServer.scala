package zio.zmx.client.backend

import zio._
import zio.metrics.jvm.DefaultJvmMetrics
import zio.zmx.notify.MetricNotifier
import zio.zmx.prometheus.PrometheusClient

import zhttp.http._
import zhttp.service._

object MetricsServer extends ZIOAppDefault {

  private val portNumber      =
    8080
  private val stopServerAfter =
    8.hours

  private val httpApp =
    Http.collectZIO[Request] {
      case Method.GET -> !!             =>
        ZIO.succeed(Response.text("Welcome to zio-zmx client"))
      case Method.GET -> !! / "ws"      =>
        WebsocketHandler.socketApp.flatMap(_.toResponse)
      case Method.GET -> !! / "metrics" =>
        PrometheusClient.snapshot
          .map(Response.text(_))
    }

  private val runSample =
    for {
      _ <- InstrumentedSample.program.fork
      s <- Server
             .start(portNumber, httpApp)
             .forever
             .fork
             .provide(
               Clock.live,
               Random.live,
               WebsocketHandler.live,
               MetricNotifier.live,
               PrometheusClient.live,
             )
      f <- ZIO.unit.schedule(Schedule.duration(stopServerAfter)).fork
      _ <- f.join *> s.interrupt
    } yield ()

  override def run: URIO[ZEnv, Unit] = {
    val trackingFlags = RuntimeConfig.default.flags + RuntimeConfigFlag.TrackRuntimeMetrics
    ZIO.withRuntimeConfig(RuntimeConfig.default.copy(flags = trackingFlags))(runSample)
  }

}

object MetricsServerWithJVMMetrics extends ZIOApp.Proxy(MetricsServer <> DefaultJvmMetrics.app)
