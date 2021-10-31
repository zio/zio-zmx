package zio.zmx.client.frontend.state

import com.raquo.laminar.api.L._
import io.laminext.websocket.WebSocket

import scala.scalajs.js.typedarray._
import upickle.default._

import java.io.PrintWriter
import java.io.StringWriter

import zio.zmx.client.MetricsMessage
import zio.zmx.client.MetricsMessage._

object WebsocketHandler {

  def render(url: String): HtmlElement = {

    val ws: WebSocket[ArrayBuffer, ArrayBuffer] = WebSocket
      .url(url)
      .arraybuffer
      .build(reconnectRetries = Int.MaxValue)

    div(
      ws.connect,
      // Initially send a "subscribe" message to kick off the stream of metric updates via Web Sockets
      ws.connected --> { _ =>
        println(s"Subscribing to Metrics messages at [$url]")
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
        new String(wrappedArr)
      } --> { msg =>
        val change: MetricsMessage = read[MetricsMessage](msg)
        MessageHub.messages.emit(change)
      },
      ws.errors --> { (t: Throwable) =>
        val w   = new StringWriter()
        val str = new PrintWriter(w)
        t.printStackTrace(str)
        println(w.toString())
        w.close()
        str.close()
      },
      ws.connected --> { _ => AppState.connected.set(true) },
      ws.closed --> { _ => AppState.connected.set(false) }
    )
  }

}
