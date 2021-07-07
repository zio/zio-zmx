package zio.zmx.client.frontend

import scala.scalajs.js
import scala.scalajs.js.typedarray.TypedArrayBufferOps._
import com.raquo.laminar.api.L._
//import io.laminext.websocket.PickleSocket.WebSocketReceiveBuilderBooPickleOps
import io.laminext.websocket.WebSocket
import boopickle.Default._
import org.scalajs.dom
import zio.zmx.client.{ ClientMessage, MetricsMessage }
import zio.zmx.client.CustomPicklers.durationPickler
import java.io.PrintWriter
import java.io.StringWriter
//import io.laminext.websocket.WebSocketEvent
import scala.scalajs.js.typedarray.ArrayBuffer
import scala.scalajs.js.typedarray.TypedArrayBuffer
import java.nio.ByteBuffer
import scala.scalajs.js.annotation.JSImport

object Main {

  def main(args: Array[String]): Unit = {

    val _ = documentEvents.onDomContentLoaded.foreach { _ =>
      val appContainer = dom.document.querySelector("#app")
      appContainer.innerHTML = ""
      val _            = render(appContainer, view)
    }(unsafeWindowOwner)
  }

  @js.native
  @JSImport("chart.js/auto", JSImport.Default)
  class Chart(ctx: dom.Element, config: js.Dynamic) extends js.Object {}

  def ChartView(): HtmlElement = {

    val dataset = js.Dynamic.literal(
      label = "demo",
//      backgroundColor = "rgb(255,99,132)",
//      borderColor = "rgb(10,10,10)",
      data = js.Array(2, 4, 6)
    )

    val config = js.Dynamic.literal(
      `type` = "line",
      data = js.Dynamic.literal(
        labels = js.Array("Foo", "Bar", "Baz"),
        datasets = js.Array(dataset)
      )
    )

    div(
      position.relative,
      width("90vw"),
      height("40vh"),
      background("#fff"),
      canvas(
        width("100%"),
        height("100%"),
        onMountCallback { el =>
          val _ = new Chart(el.thisNode.ref, config)
        }
      )
    )
  }

  val ws: WebSocket[ArrayBuffer, ArrayBuffer] =
    WebSocket
      .url("ws://devel.wayofquality.de:8089/ws")
      .arraybuffer
      //.pickle[MetricsMessage, ClientMessage]
      .build(reconnectRetries = Int.MaxValue)

  // def GaugeView(key: MetricKey.Gauge, $gauge: Signal[GaugeChange]): Div = {
  //   println(s"GAUGE CREATION ${key}")
  //   val _        = $gauge
  //   var minGauge = Double.MaxValue
  //   var maxGauge = Double.MinValue

  //   val $offset: Signal[(Double, Double, Double)] =
  //     $gauge.map { gauge =>
  //       val value = gauge.value
  //       // println(s"VALUE: ${value}")
  //       // println(s"MIN: ${minGauge}")
  //       // println(s"MAX: ${maxGauge}")
  //       minGauge = value min minGauge
  //       maxGauge = value max maxGauge

  //       val offset =
  //         if ((maxGauge - minGauge) == 0) 0
  //         else (value - minGauge) / (maxGauge - minGauge)

  //       (minGauge, offset, maxGauge)
  //     }

  //   div(
  //     div(
  //       strong(key.name)
  //     ),
  //     pre(child.text <-- $offset.map(_._3.toString)),
  //     div(
  //       height("80px"),
  //       width("40px"),
  //       background("#333"),
  //       div(
  //         height("3px"),
  //         width("40px"),
  //         background("blue"),
  //         position.relative,
  //         top <-- $offset.map(_._2 * 77.0).spring.px
  //       )
  //     ),
  //     pre(child.text <-- $gauge.map(_.value.toString)),
  //     pre(child.text <-- $offset.map(_._1.toString))
  //   )
  // }

  // def messagesView: Div = div(
  //   children <-- messagesVar.signal.map(_.values.toList).split(_.key) { (key, _, $metric) =>
  //     key match {
  //       case key: MetricKey.Gauge =>
  //         GaugeView(key, $metric.asInstanceOf[Signal[GaugeChange]])
  //       case _                    => div("OOPS")
  //     }
  //   }
  // )

  def view: Div =
    div(
      margin("0"),
      padding("20px"),
      position.relative,
      height("100vh"),
      width("100vw"),
      "My METRICS",
      AppViews.summaries,
      ws.connect,
      ws.connected --> { _ =>
        println("Subscribing to Metrics messages")
        val subscribe: ByteBuffer = Pickle.intoBytes[ClientMessage](ClientMessage.subscribe)
        ws.sendOne(subscribe.arrayBuffer())
      },
      ws.received --> { (buf: ArrayBuffer) =>
        val metricsMessage = Unpickle[MetricsMessage].fromBytes(TypedArrayBuffer.wrap(buf))
        AppState.updateSummary(metricsMessage)
      },
      ws.errors --> { (t: Throwable) =>
        val w   = new StringWriter()
        val str = new PrintWriter(w)
        t.printStackTrace(str)
        println(w.toString())
        w.close()
        str.close()
      },
      ws.connected --> { _ => println("Connected to WebSocket") }
    )
}
