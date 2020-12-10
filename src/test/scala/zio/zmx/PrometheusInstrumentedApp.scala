package zio.zmx

import java.net.InetSocketAddress
import uzhttp.server.Server
import uzhttp._
import zio._
import zio.console._
import zio.zmx.metrics._
import zio.zmx.prometheus._

object PrometheusInstrumentedApp extends ZmxApp with InstrumentedSample {

  private val bindHost = "127.0.0.1"
  private val bindPort = 8080

  override def makeInstrumentation = PrometheusRegistry.make.map(r => new PrometheusInstrumentaion(r))

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      _ <- Server
             .builder(new InetSocketAddress(bindHost, bindPort))
             .handleSome {
               case req if req.uri.getPath() == "/"      =>
                 ZIO.succeed(Response.html("<html><title>Simple Server</title><a href=\"/metrics\">Metrics</a></html>"))
               case req if req.uri.getPath == "/metrics" =>
                 instrumentation.flatMap(i => i.report.map(r => Response.plain(r)))
             }
             .serve
             .use(s => s.awaitShutdown)
             .fork
      _ <- putStrLn("Press Any Key")
      _ <- program.fork
      f <- getStrLn.fork
      _ <- f.join.catchAll(_ => ZIO.none)
    } yield ExitCode.success)

}
