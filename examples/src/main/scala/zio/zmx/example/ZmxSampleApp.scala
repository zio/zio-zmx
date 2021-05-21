package zio.zmx.example

import java.net.InetSocketAddress
import java.time.Instant

import uzhttp._
import uzhttp.server.Server

import zio._
import zio.console._
import zio.zmx._
import zio.zmx.encode._
import zio.zmx.statsd.StatsdListener
import zio.zmx.statsd.StatsdClient
import zio.zmx.statsd.StatsdConfig

object ZmxSampleApp extends App with InstrumentedSample {

  private val bindHost = "0.0.0.0"
  private val bindPort = 8080

  object path {
    def unapply(req: Request): Option[String] =
      Some(req.uri.getPath)
  }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      _ <- StatsdListener.make.map(installListener(_))
      _ <- Server
             .builder(new InetSocketAddress(bindHost, bindPort))
             .handleSome {
               case path("/")        =>
                 ZIO.succeed(
                   Response.html(
                     """<html>
                       |<title>Simple Server</title>
                       |<body>
                       |<p><a href="/metrics">Metrics</a></p>
                       |<p><a href="/json">Json</a></p>
                       |</body
                       |</html>""".stripMargin
                   )
                 )
               case path("/metrics") =>
                 val content = PrometheusEncoder.encode(snapshot().values, Instant.now())
                 ZIO.succeed(Response.plain(content))
               case path("/json")    =>
                 val content = JsonEncoder.encode(snapshot().values)
                 ZIO.succeed(Response.plain(content, headers = List("Content-Type" -> "application/json")))
             }
             .serve
             .use(s => s.awaitShutdown)
             .fork
      _ <- putStrLn("Press Any Key")
      _ <- program.fork
      f <- getStrLn.fork
      _ <- f.join.catchAll(_ => ZIO.none)
    } yield ExitCode.success).provideCustomLayer(StatsdClient.live(StatsdConfig.default).orDie)
}
