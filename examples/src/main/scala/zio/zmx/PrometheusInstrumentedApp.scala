package zio.zmx

import java.net.InetSocketAddress
import uzhttp.server.Server
import uzhttp._
import zio._
import zio.console._
import zio.zmx.metrics._
import zio.zmx.prometheus._

object PrometheusInstrumentedApp extends ZmxApp with InstrumentedSample {

  private val bindHost = "0.0.0.0"
  private val bindPort = 8080

  private val demoQs: Seq[Quantile] =
    1.to(9).map(i => i / 10d).map(d => Quantile(d, 0.03)).collect { case Some(q) => q }

  // For all histograms set the buckets to some linear slots
  private val cfg                   = PrometheusConfig(
    // Use a linear bucket scale for all histograms
    buckets = Chunk(_ => Some(PMetric.Buckets.Linear(10, 10, 10))),
    // Use the demo Quantiles for all summaries
    quantiles = Chunk(_ => Some(demoQs))
  )

  override def makeInstrumentation = PrometheusRegistry.make(cfg).map(r => new PrometheusInstrumentaion(r))

  override def runInstrumented(args: List[String], inst: Instrumentation): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      _ <- Server
             .builder(new InetSocketAddress(bindHost, bindPort))
             .handleSome {
               case req if req.uri.getPath() == "/"      =>
                 ZIO.succeed(Response.html("<html><title>Simple Server</title><a href=\"/metrics\">Metrics</a></html>"))
               case req if req.uri.getPath == "/metrics" =>
                 inst.report.map(r => Response.plain(r))
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
