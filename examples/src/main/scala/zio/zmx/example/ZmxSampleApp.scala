package zio.zmx.example

import zhttp.http._
import zhttp.service._
import zio.metrics.jvm.DefaultJvmMetrics
import zio.zmx.prometheus.PrometheusClient
import zio._

object ZmxSampleApp extends ZIOAppDefault with InstrumentedSample {

  private val portNumber = 8080

  private val httpApp =
    Http
      .collectZIO[Request] { case Method.GET -> !! / "metrics" =>
        PrometheusClient.snapshot
          .map(Response.text(_))
      }

  override def run: RIO[ZEnv, Unit] =
    for {
      _ <- sampleProgram.fork
      s <- Server
             .start(portNumber, httpApp)
             .forever
             .fork
             .provide(
               PrometheusClient.live
             )
      f <- (Console.printLine("Press any key to stop HTTP server") *> Console.readLine).fork
      _ <- f.join *> s.interrupt
    } yield ()
}

object ZmxSampleAppWithJvmMetrics extends ZIOApp.Proxy(ZmxSampleApp <> DefaultJvmMetrics.app)
