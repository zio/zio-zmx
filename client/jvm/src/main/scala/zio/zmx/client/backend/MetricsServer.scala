package zio.zmx.client.backend

import boopickle.Default._
import io.netty.buffer.Unpooled
import zhttp.core.ByteBuf
import zhttp.http._
import zhttp.service._
import zhttp.socket.{ Socket, WebSocketFrame }
import zio._
import zio.stream.ZStream
import zio.zmx.client.ClientMessage
import zio.zmx.client.CustomPicklers.{ durationPickler, instantPickler }

import scala.util.{ Failure, Success, Try }
import zio.zmx.client.MetricsMessage

object MetricsServer {

  private lazy val appSocket =
    pickleSocket { (command: ClientMessage) =>
      command match {
        case ClientMessage.Subscribe =>
          println("SUBSCRIBED")
          MetricsProtocol.statsStream.map { state =>
            // https://github.com/suzaku-io/boopickle/issues/170 Pickle/Unpickle derivation doesnt work with sealed traits in scala 3
            import MetricsMessage._
            val byteBuf = state match {
              case change: GaugeChange     => Unpooled.wrappedBuffer(Pickle.intoBytes(change))
              case change: CounterChange   => Unpooled.wrappedBuffer(Pickle.intoBytes(change))
              case change: HistogramChange => Unpooled.wrappedBuffer(Pickle.intoBytes(change))
              case change: SummaryChange   => Unpooled.wrappedBuffer(Pickle.intoBytes(change))
              case change: SetChange       => Unpooled.wrappedBuffer(Pickle.intoBytes(change))
            }

            WebSocketFrame.binary(ByteBuf(byteBuf))
          }
      }
    }

  private lazy val app =
    HttpApp.collect { case Method.GET -> Root / "ws" =>
      Response.socket(appSocket)
    }

  private lazy val program =
    for {
      _ <- Console.printLine("STARTING SERVER")
      _ <- InstrumentedSample.program.fork
      _ <- Server.start(8089, app)
    } yield ()

  private lazy val layer = MetricsProtocol.live

  private def pickleSocket[R, E, A: Pickler](
    f: A => ZStream[R, E, WebSocketFrame]
  ): Socket[Console with R, E, WebSocketFrame, WebSocketFrame] =
    Socket.collect {
      case WebSocketFrame.Binary(bytes) =>
        Try(Unpickle[A].fromBytes(bytes.asJava.nioBuffer())) match {
          case Failure(error)   =>
            ZStream.fromEffect(Console.putStrErr(s"Decoding Error: $error").orDie).drain
          case Success(command) =>
            f(command)
        }
      case other                        =>
        ZStream.fromEffect(UIO(println(s"RECEIVED $other"))).drain
    }

}
