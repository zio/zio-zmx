package zio.zmx.client.backend

import boopickle.Default._
import io.netty.buffer.Unpooled
import zhttp.core.ByteBuf
import zhttp.http._
import zhttp.service._
import zhttp.socket.{ Socket, WebSocketFrame }
import zio._
import zio.console._
import zio.stream.ZStream
import zio.zmx.client.ClientMessage
import zio.zmx.client.CustomPicklers.durationPickler

import scala.util.{ Failure, Success, Try }

object MetricsServer extends App {

  val appSocket =
    pickleSocket { (command: ClientMessage) =>
      command match {
        case ClientMessage.Subscribe =>
          println("SUBSCRIBED")
          MetricsProtocol.statsStream.map { state =>
            println(s"BROADCASTING $state")
            val byteBuf = Unpooled.wrappedBuffer(Pickle.intoBytes(state))
            WebSocketFrame.binary(ByteBuf(byteBuf))
          }
      }
    }

  val app =
    HttpApp.collect { case Method.GET -> Root / "ws" =>
      Response.socket(appSocket)
    }

  val program =
    for {
      _ <- putStrLn("STARTING SERVER")
      _ <- InstrumentedSample.program.fork
      _ <- Server.start(8089, app)
    } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    program.provideCustomLayer(MetricsProtocol.live).exitCode

  private def pickleSocket[R, E, A: Pickler](
    f: A => ZStream[R, E, WebSocketFrame]
  ): Socket[Console with R, E, WebSocketFrame, WebSocketFrame] =
    Socket.collect {
      case WebSocketFrame.Binary(bytes) =>
        println(s"Trying to pickle incoming message")
        Try(Unpickle[A].fromBytes(bytes.asJava.nioBuffer())) match {
          case Failure(error)   =>
            ZStream.fromEffect(putStrErr(s"Decoding Error: $error").orDie).drain
          case Success(command) =>
            println(s"Pickled : $command")
            f(command)
        }
      case other                        =>
        ZStream.fromEffect(UIO(println(s"RECEIVED $other"))).drain
    }

}
