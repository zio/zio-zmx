package zio.zmx.client.backend

import java.nio.charset.StandardCharsets.UTF_8

import zio._
import zio.json._
import zio.stream.{Stream, Take, UStream, ZStream}
import zio.zmx.client.ClientMessage
import zio.zmx.client.ClientMessage._
import zio.zmx.notify.MetricNotifier

import io.netty.buffer.ByteBuf
import zhttp.socket.{Socket, SocketApp, WebSocketFrame}

trait WebsocketHandler {
  def socketApp: SocketApp[Any]
}

object WebsocketHandler {

  val live: URLayer[MetricNotifier, WebsocketHandlerLive] =
    ZIO
      .service[MetricNotifier]
      .map(WebsocketHandlerLive)
      .toLayer

  val socketApp: URIO[WebsocketHandler, SocketApp[Any]] =
    ZIO.serviceWith(_.socketApp)

  final case class WebsocketHandlerLive(notifier: MetricNotifier) extends WebsocketHandler {
    override val socketApp: SocketApp[Any] =
      Socket
        .collect[WebSocketFrame] {
          case WebSocketFrame.Binary(data)       =>
            processMessage(
              clientMessageFromByteChunk(data),
            )
          case WebSocketFrame.Text(data)         =>
            processMessage(
              clientMessageFromString(data),
            )
          case WebSocketFrame.Continuation(data) =>
            processMessage(
              clientMessageFromByteBuffer(data),
            )
          case websocketFrame                    =>
            ZStream.fromZIO(
              ZIO.logWarning(s"Unexpected WebSocket frame: <$websocketFrame>."),
            ) *>
              Stream.empty
        }
        .toSocketApp

    private def processMessage(readMessage: Task[Option[ClientMessage]]) =
      ZStream
        .fromZIO(
          readMessage.debug("Received message from client"),
        )
        .flatMap {
          case Some(Connect)                                  =>
            connect().map { message =>
              val reply = message.toJson.getBytes
              Take.single(
                WebSocketFrame.Binary(Chunk.fromIterable(reply)),
              )
            }.flattenTake
          case Some(Disconnect(clientId))                     =>
            ok(
              notifier.disconnect(clientId),
            )
          case Some(Subscription(client, id, keys, interval)) =>
            ok(
              notifier.subscribe(client, id, Chunk.fromIterable(keys), interval),
            )
          case Some(RemoveSubscription(client, id))           =>
            ok(
              notifier.unsubscribe(client, id),
            )
          case _                                              =>
            ok(ZIO.unit)
        }

    private def connect(): UStream[ClientMessage] = {
      val processConnectMessage =
        notifier
          .connect()
          .map { case (clientId, updateStream, keyStream) =>
            val connectedEvent =
              ZStream.from(ClientMessage.Connected(clientId))
            val metrics        =
              updateStream
                .map { update =>
                  ClientMessage.MetricsNotification(update.clt, update.subId, update.when, update.states)
                }
                .tap { msg =>
                  ZIO.logInfo(s"Sending update to client ($clientId): <$msg>")
                }
            val keys           = keyStream.map(kc => ClientMessage.AvailableMetrics(kc))

            connectedEvent ++ metrics.merge(keys)
          }

      ZStream.fromZIO(processConnectMessage).flatten
    }
  }

  private val doneStream =
    Stream(Take.end).flattenTake

  private def clientMessageFromString(text: String): Task[Option[ClientMessage]] =
    ZIO
      .fromEither(
        text.fromJson[ClientMessage],
      )
      .map(Some(_))
      .catchAll(e => ZIO.logError(s"Failed to parse message from client: <$e>").as(None))

  private def clientMessageFromByteChunk(bytes: Chunk[Byte]) =
    ZIO
      .attempt[String](
        new String(bytes.toArray),
      )
      .flatMap(
        clientMessageFromString,
      )

  private def clientMessageFromByteBuffer(buffer: ByteBuf) =
    ZIO
      .attempt[String](
        buffer.toString(UTF_8),
      )
      .flatMap(
        clientMessageFromString,
      )

  private def ok(effect: UIO[_]) =
    ZStream.fromZIO(effect) *> doneStream
}
