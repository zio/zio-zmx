package zio.zmx.example

import uzhttp._
import uzhttp.server._
import zio._
import zio.zmx.statsd.StatsdClient
import zio.zmx.prometheus.PrometheusClient
import java.net.InetSocketAddress

object ZmxSampleApp extends ZIOAppDefault with InstrumentedSample {

  private val bindHost = "0.0.0.0"
  private val bindPort = 8080

  private val server = Server
    .builder(new InetSocketAddress(bindHost, bindPort))
    .handleSome {
      case req if req.uri.getPath.equals("/metrics") =>
        PrometheusClient.snapshot.map(resp => Response.plain(resp))
    }
    .serve

  override def run: ZIO[Environment with ZEnv with Has[ZIOAppArgs], Any, Any] = for {
    _ <- program
    s <- server.useForever.orDie.provideCustomLayer(PrometheusClient.live ++ StatsdClient.default).fork
    f <- Console.printLine(s"Press ENTER to stop HTTP server").flatMap(_ => Console.readLine).fork
    _ <- f.join.flatMap(_ => s.interrupt)
  } yield ()

}
