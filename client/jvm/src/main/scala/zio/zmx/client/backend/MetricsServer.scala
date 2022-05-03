package zio.zmx.client.backend

import zio._
import zio.zmx._
import zio.zmx.notify.MetricNotifier

import zhttp.http._
import zhttp.service._

object MetricsServer
    extends ZMXApp.Default[Any](
      ZLayer.empty,
      ZMXApp.Settings.live,
    ) {

  private val portNumber      = 8080
  private val stopServerAfter = 8.hours

  private val httpApp =
    Http.collectZIO[Request] {
      case Method.GET -> !!        =>
        ZIO.succeed(Response.text("Welcome to the ZIO-ZMX 2.0 client"))
      case Method.GET -> !! / "ws" =>
        WebsocketHandler.socketApp.flatMap(_.toResponse)
    }

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

  def run = runSample.provide(
    WebsocketHandler.live,
    MetricNotifier.live,
  )
}
