package zio.zmx.client.backend

import uzhttp._
import uzhttp.server._
import uzhttp.websocket._

import boopickle.Default._
import zio._
import zio.stream._
import zio.zmx.client.ClientMessage
import zio.zmx.client.MetricsMessage

import zio.zmx.client.CustomPicklers.{ durationPickler, instantPickler }
import scala.util.{ Failure, Success, Try }

import java.net.InetSocketAddress
import java.nio.ByteBuffer

object MetricsServer extends ZIOAppDefault {

  private val bindHost        = "0.0.0.0"
  private val bindPort        = 8080
  private val stopServerAfter = 5.minutes

  private lazy val appSocket: Stream[Throwable, Frame] => Stream[Throwable, Frame] = input =>
    pickleSocket(input) { (command: ClientMessage) =>
      command match {
        case ClientMessage.Subscribe =>
          println("SUBSCRIBED")
          (MetricsProtocol.statsStream.map { state =>
            println(state.toString())
            // https://github.com/suzaku-io/boopickle/issues/170 Pickle/Unpickle derivation doesnt work with sealed traits in scala 3
            import MetricsMessage._
            val byteBuf = state match {
              case change: GaugeChange     => Pickle.intoBytes(change)
              case change: CounterChange   => Pickle.intoBytes(change)
              case change: HistogramChange => Pickle.intoBytes(change)
              case change: SummaryChange   => Pickle.intoBytes(change)
              case change: SetChange       => Pickle.intoBytes(change)
            }

            Binary(byteBuf.array())
          }).provideSomeLayer(MetricsProtocol.live)
      }
    }

  private def pickleSocket[R, Throwable, A: Pickler](input: Stream[Throwable, Frame])(
    f: A => Stream[Throwable, Frame]
  ) =
    (input.collect {
      case Binary(data, _) =>
        Try(Unpickle[A].fromBytes(ByteBuffer.wrap(data))) match {
          case Failure(error)   =>
            println(s"Decoding error: $error")
            Stream.empty
          case Success(command) =>
            f(command)
        }
      case other           =>
        println(other)
        Stream.empty
    }).flatten

  private val server = Server
    .builder(new InetSocketAddress(bindHost, bindPort))
    .handleSome {
      case req if req.uri.getPath.equals("/")                                                         => ZIO.succeed(Response.html("Hello Andreas!"))
      case req @ Request.WebsocketRequest(_, uri, _, _, inputFrames) if uri.getPath.startsWith("/ws") =>
        Response.websocket(req, appSocket(inputFrames))
    }
    .serve

  override def run = for {
    _ <- InstrumentedSample.program.fork
    s <- server.useForever.orDie.fork
    f <- ZIO.unit.schedule(Schedule.duration(stopServerAfter)).fork
    _ <- f.join.flatMap(_ => s.interrupt)
  } yield ()

}
