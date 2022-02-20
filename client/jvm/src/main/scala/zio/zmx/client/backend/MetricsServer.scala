package zio.zmx.client.backend

import zhttp.http._
import zhttp.service._
import zio.metrics.jvm.DefaultJvmMetrics
import zio.zmx.notify.MetricNotifier
import zio._

object MetricsServer extends ZIOAppDefault {

  private val portNumber      =
    8080
  private val stopServerAfter =
    8.hours

  private val httpApp =
    Http.collectZIO[Request] {
      case Method.GET -> !!        =>
        UIO(Response.text("Welcome to zio-zmx client"))
      case Method.GET -> !! / "ws" =>
        WebsocketHandler.socketApp.flatMap(_.toResponse)
    }

  override def run: URIO[ZEnv, Unit] =
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
               MetricNotifier.live
             )
      f <- ZIO.unit.schedule(Schedule.duration(stopServerAfter)).fork
      _ <- f.join *> s.interrupt
    } yield ()
}

object MetricsServerWithJVMMetrics extends ZIOApp.Proxy(MetricsServer <> DefaultJvmMetrics.app)
