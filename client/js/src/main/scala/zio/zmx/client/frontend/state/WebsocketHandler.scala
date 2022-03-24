package zio.zmx.client.frontend.state

import com.raquo.laminar.api.L._
import io.laminext.websocket.WebSocket

import scala.scalajs.js.typedarray._

import java.io.PrintWriter
import java.io.StringWriter

import zio.zmx.client.ClientMessage
import zio.zmx.client.ClientMessage._

object WebsocketHandler {

  type ArrayBufferWebSocket = WebSocket[ArrayBuffer, ArrayBuffer]

  def create(url: String): ArrayBufferWebSocket =
    newArrayBufferWebSocket(url)
      .build(
        reconnectRetries = Int.MaxValue,
        managed = false
      )

  val wsFrame: ClientMessage => ArrayBuffer = msg => {
    val json = msg.toJson
    byteArray2Int8Array(json.getBytes()).buffer
  }

  def sendCommand(msg: ClientMessage): Unit = {
    val url   = AppState.connectUrl.now()
    val wsCmd = newArrayBufferWebSocket(url).build(
      managed = false,
      autoReconnect = false
    )

    println(s"Sending WS message <$msg> to <$url>")
    try wsCmd.reconnect
      .onNext(
        wsCmd.sendOne(wsFrame(msg))
      )
    catch {
      case t: Throwable => t.printStackTrace()
    }
  }

  def mountWebsocket(ws: ArrayBufferWebSocket): HtmlElement =
    div(
      ws.connected --> { _ =>
        println(s"Subscribing to Metrics messages")
        AppState.wsConnection.set(Some(ws))
        ws.sendOne(wsFrame(ClientMessage.Connect))
      },
      ws.received.map { buf =>
        val wrappedBuf = TypedArrayBuffer.wrap(buf)
        wrappedBuf.rewind()
        val wrappedArr = new Array[Byte](wrappedBuf.remaining())
        wrappedBuf.get(wrappedArr)
        read[ClientMessage](
          new String(wrappedArr)
        )
      }.map(msg => Command.ServerMessage(msg)) --> Command.observer,
      ws.errors --> { error =>
        // Should use 2.13's `scala.util.Using.Manager` here once we get rid of 2.12.
        val writer  = new StringWriter()
        val printer = new PrintWriter(writer)
        try {
          error.printStackTrace(printer)
          println(s"$writer")
        } finally {
          writer.close()
          printer.close()
        }
      },
      ws.closed --> { _ =>
        println("WebSocket connection has been closed.")
      }
    )

  private def newArrayBufferWebSocket(url: String) =
    WebSocket
      .url(url)
      .arraybuffer
}
