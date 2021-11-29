package zio.zmx.client.backend

import uzhttp._
import uzhttp.server._
import uzhttp.websocket._

import zio._
import zio.stream._
import zio.zmx.client.ClientMessage
import zio.zmx.client.MetricsMessage._
import upickle.default._

import java.net.InetSocketAddress

object MetricsServer extends ZIOAppDefault {

  private val bindHost        = "0.0.0.0"
  private val bindPort        = 8080
  private val stopServerAfter = 8.hours

  private lazy val appSocket: Stream[Throwable, Frame] => Stream[Throwable, Frame] = input =>
    pickleSocket(input) { (command: ClientMessage) =>
      command match {
        case ClientMessage.Subscribe =>
          println("SUBSCRIBED")
          (MetricsProtocol.statsStream.map { state =>
            val json = write(state)
            println(json)
            Binary(json.getBytes())
          }).provideSome(MetricsProtocol.live)
      }
    }

  private def pickleSocket[R, Throwable](input: ZStream[Any, Throwable, Frame])(
    f: ClientMessage => ZStream[Any, Throwable, Frame]
  ): ZStream[Any, Throwable, Frame] =
    (input.collect {
      case Text(data, _)   =>
        println(data)
        if (data.equalsIgnoreCase("subscribe")) f(ClientMessage.subscribe) else Stream.empty
      case Binary(data, _) =>
        println(data.toString)
        val msg = new String(data)
        println("Message from client: " + msg)
        if (msg.equalsIgnoreCase("subscribe")) f(ClientMessage.subscribe) else Stream.empty
      case other           =>
        println("Received " + other.toString())
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
