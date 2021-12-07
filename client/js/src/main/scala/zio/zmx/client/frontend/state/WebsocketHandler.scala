package zio.zmx.client.frontend.state

import com.raquo.laminar.api.L._
import io.laminext.websocket.WebSocket

import scala.scalajs.js.typedarray._
import upickle.default._

import java.io.PrintWriter
import java.io.StringWriter

import zio.zmx.client.MetricsUpdate
import zio.zmx.client.MetricsUpdate._

object WebsocketHandler {

  def create(url: String): WebSocket[ArrayBuffer, ArrayBuffer] =
    WebSocket
      .url(url)
      .arraybuffer
      .build(reconnectRetries = Int.MaxValue, managed = false)

  def mountWebsocket(ws: WebSocket[ArrayBuffer, ArrayBuffer]): HtmlElement =
    div(
      ws.connected --> { _ =>
        println(s"Subscribing to Metrics messages")
        val subscribe = byteArray2Int8Array("subscribe".getBytes()).buffer
        ws.sendOne(subscribe)
      },
      // Whenever we receive a message we decode it into a MetricMessage and simply emit that on our Laminar
      // stream of events
      ws.received.map { buf =>
        val wrappedBuf = TypedArrayBuffer.wrap(buf)
        wrappedBuf.rewind()
        val wrappedArr = new Array[Byte](wrappedBuf.remaining())
        wrappedBuf.get(wrappedArr)
        val msg        = new String(wrappedArr)
        read[MetricsUpdate](msg)
      }.map(msg => Command.RecordData(msg)) --> Command.observer,
      ws.errors --> { (t: Throwable) =>
        val w   = new StringWriter()
        val str = new PrintWriter(w)
        t.printStackTrace(str)
        println(w.toString())
        w.close()
        str.close()
      },
      ws.connected --> { _ =>
        println("Connected to Server")
        AppState.wsConnection.set(Some(ws))
      }
    )

}
