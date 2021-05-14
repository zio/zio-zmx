package zio.zmx.example

import java.net.InetSocketAddress
import java.time.Instant

import uzhttp._
import uzhttp.server.Server

import zio._
import zio.console._
import zio.zmx._
import zio.zmx.encode._

object PrometheusInstrumentedApp extends App with InstrumentedSample {

  private val bindHost = "0.0.0.0"
  private val bindPort = 8080

  object path {
    def unapply(req: Request): Option[String] =
      Some(req.uri.getPath)
  }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      _ <- Server
             .builder(new InetSocketAddress(bindHost, bindPort))
             .handleSome {
               case path("/")        =>
                 ZIO.succeed(Response.html("<html><title>Simple Server</title><a href=\"/metrics\">Metrics</a></html>"))
               case path("/metrics") =>
                 val state   = snapshot()
                 val content = PrometheusEncoder.encode(state.values, Instant.now())
                 ZIO.succeed(Response.plain(content))
               case path("/json")    =>
                 val state   = snapshot()
                 val content = JsonEncoder.encode(state.values)
                 ZIO.succeed(Response.plain(content, headers = List("Content-Type" -> "application/json")))
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
