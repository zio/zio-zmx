package zio.zmx.client.frontend.state

import com.raquo.laminar.api.L._
import io.laminext.websocket.WebSocket

import scala.scalajs.js.typedarray._
import upickle.default._

import java.io.PrintWriter
import java.io.StringWriter

import zio.zmx.client.ClientMessage

object WebsocketHandler {

  def create(url: String): WebSocket[ArrayBuffer, ArrayBuffer] =
    WebSocket
      .url(url)
      .arraybuffer
      .build(reconnectRetries = Int.MaxValue, managed = false)

  val wsFrame: ClientMessage => ArrayBuffer = msg => {
    val json = write(msg)
    byteArray2Int8Array(json.getBytes()).buffer
  }

  def sendCommand(msg: ClientMessage): Unit = {
    val url   = AppState.connectUrl.now()
    val wsCmd = WebSocket.url(url).arraybuffer.build(managed = false, autoReconnect = false)
    val f     = wsFrame(msg)
    println(s"Sending WS message <$msg> to <$url>")
    try {
      wsCmd.reconnectNow()
      wsCmd.sendOne(f)
    } catch {
      case t: Throwable => t.printStackTrace()
    }
  }

  def mountWebsocket(ws: WebSocket[ArrayBuffer, ArrayBuffer]): HtmlElement =
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
        val msg        = new String(wrappedArr)
        read[ClientMessage](msg)
      }.map(msg => Command.ServerMessage(msg)) --> Command.observer,
      ws.errors --> { (t: Throwable) =>
        val w   = new StringWriter()
        val str = new PrintWriter(w)
        t.printStackTrace(str)
        println(w.toString())
        w.close()
        str.close()
      },
      ws.closed --> { _ =>
        println("WebSocket connection has been closed.")
      }
    )

}
