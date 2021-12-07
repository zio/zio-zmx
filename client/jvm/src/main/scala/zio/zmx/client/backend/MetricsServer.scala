package zio.zmx.client.backend

import uzhttp._
import uzhttp.server._
import uzhttp.websocket._

import zio._
import zio.stream._
import zio.zmx.client.ClientMessage
import zio.zmx.client.MetricsUpdate
import upickle.default._

import java.net.InetSocketAddress
import zio.metrics.jvm.DefaultJvmMetrics
import zio.zmx.notify.MetricNotifier
import scala.util.Try

object MetricsServer extends ZIOAppDefault {

  private val bindHost        = "0.0.0.0"
  private val bindPort        = 8080
  private val stopServerAfter = 8.hours

  trait WSHandler {
    def handleZMXSocket(input: Stream[Throwable, Frame]): Stream[Throwable, Frame]
  }

  case class WSHandlerImpl(notifier: MetricNotifier) extends WSHandler {
    def handleZMXSocket(input: Stream[Throwable, Frame]): Stream[Throwable, Frame] = appSocket(input)

    private lazy val appSocket: Stream[Throwable, Frame] => Stream[Throwable, Frame] = input =>
      pickleSocket(input) { command: ClientMessage =>
        command match {
          case ClientMessage.Connect =>
            for {
              connected <- notifier.connect()
              states     = connected._2
              frames     =
                states.map { st =>
                  val cltMsg =
                    ClientMessage.MetricsNotification(Chunk.fromIterable(st.map { case (k, s) =>
                      MetricsUpdate.fromMetricState(k, s)
                    }))
                  Binary(write(cltMsg).getBytes)
                }
            } yield frames
          case o                     =>
            ZIO.logDebug(s"$o") *> ZIO.succeed(Stream.empty)
        }
      }.flatten

    private def pickleString(s: String)(f: ClientMessage => UIO[Stream[Throwable, Frame]]) = for {
      _   <- ZIO.logInfo(s"$s")
      msg  = Try(read[ClientMessage](s)).toOption
      str <- msg match {
               case None    => ZIO.succeed(Stream.empty)
               case Some(m) => f(m)
             }
    } yield str

    private def pickleSocket(input: ZStream[Any, Throwable, Frame])(
      f: ClientMessage => UIO[Stream[Throwable, Frame]]
    ) =
      input.collectZIO {
        case Text(data, _)   => pickleString(data)(f)
        case Binary(data, _) => pickleString(new String(data))(f)
        case o               => ZIO.logDebug(s"Received $o") *> ZIO.succeed(Stream.empty)
      }
  }

  object WSHandler {

    val live = ZIO.service[MetricNotifier].map(n => WSHandlerImpl(n)).toLayer
  }

  private val server = Server
    .builder(new InetSocketAddress(bindHost, bindPort))
    .handleSome {
      case req if req.uri.getPath.equals("/")                                                         => ZIO.succeed(Response.html("Hello Andreas!"))
      case req @ Request.WebsocketRequest(_, uri, _, _, inputFrames) if uri.getPath.startsWith("/ws") =>
        ZIO.serviceWith[WSHandler](_.handleZMXSocket(inputFrames)).flatMap(as => Response.websocket(req, as))
    }
    .serve

  override def run = for {
    _ <- InstrumentedSample.program.fork
    s <- server.useForever.orDie.fork.provide(Clock.live, Random.live, WSHandler.live, MetricNotifier.live)
    f <- ZIO.unit.schedule(Schedule.duration(stopServerAfter)).fork
    _ <- f.join.flatMap(_ => s.interrupt)
  } yield ()
}

object MetricsServerWithJVMMetrics extends ZIOApp.Proxy(MetricsServer <> DefaultJvmMetrics.app)
