package zio.zmx.client.frontend

import java.nio.ByteBuffer
import scala.scalajs.js.typedarray.ArrayBuffer
import scala.scalajs.js.typedarray.TypedArrayBuffer
import scala.scalajs.js.typedarray.TypedArrayBufferOps._

import com.raquo.laminar.api.L._
import io.laminext.websocket.WebSocket

import zio.zmx.internal.MetricKey
import AppDataModel._

import zio.Chunk
import zio.zmx.client.frontend.AppDataModel.MetricSummary._

import zio.zmx.client.{ ClientMessage, MetricsMessage }
import java.io.PrintWriter
import java.io.StringWriter

import boopickle.Default._
import zio.zmx.client.CustomPicklers.durationPickler
import zio.zmx.client.frontend.views.DiagramView
import zio.zmx.client.MetricsMessage.GaugeChange
import zio.zmx.client.MetricsMessage.CounterChange
import zio.zmx.client.MetricsMessage.HistogramChange
import zio.zmx.client.MetricsMessage.SummaryChange
import zio.zmx.client.MetricsMessage.SetChange
import org.w3c.dom.css.Counter

object AppState {

  val diagrams: Var[Chunk[DiagramView]] = Var(Chunk.empty)

  def addCounterDiagram(key: String) = diagrams.update(_ :+ DiagramView.counterDiagram(key))

  lazy val messages: Var[MetricsMessage] = {
    val res: Var[MetricsMessage] = Var(
      MetricsMessage.CounterChange(MetricKey.Counter("foo"), 0, 0) // Just a dummy message
    )

    res.signal.toWeakSignal.changes.collect { case Some(m) => m }.foreach { msg =>
      msg match {
        case GaugeChange(key, value, delta) => ()
        case cnt: CounterChange             => counterMessages.set(Some(cnt))
        case HistogramChange(key, value)    => ()
        case SummaryChange(key, value)      => ()
        case SetChange(key, value)          => ()
      }
      MetricSummary.fromMessage(msg).foreach(sum => summaries.update(_.updated(msg.key, sum)))
    }(unsafeWindowOwner)

    res
  }

  lazy val counterMessages: Var[Option[MetricsMessage.CounterChange]] = Var(None)

  val counterInfo: Signal[Chunk[CounterInfo]]                    =
    summaries.signal.map(_.collect { case (_, ci: CounterInfo) => ci }).map(Chunk.fromIterable)

  val gaugeInfo: Signal[Chunk[GaugeInfo]]                        =
    summaries.signal.map(_.collect { case (_, ci: GaugeInfo) => ci }).map(Chunk.fromIterable)

  val histogramInfo: Signal[Chunk[HistogramInfo]]                =
    summaries.signal.map(_.collect { case (_, ci: HistogramInfo) => ci }).map(Chunk.fromIterable)

  val summaryInfo: Signal[Chunk[SummaryInfo]]                    =
    summaries.signal.map(_.collect { case (_, ci: SummaryInfo) => ci }).map(Chunk.fromIterable)

  val setInfo: Signal[Chunk[SetInfo]]                            =
    summaries.signal.map(_.collect { case (_, ci: SetInfo) => ci }).map(Chunk.fromIterable)

  private lazy val summaries: Var[Map[MetricKey, MetricSummary]] = Var(Map.empty)

  lazy val ws: WebSocket[ArrayBuffer, ArrayBuffer] =
    WebSocket
      .url("ws://devel.wayofquality.de:8089/ws")
      .arraybuffer
      //.pickle[MetricsMessage, ClientMessage]
      .build(reconnectRetries = Int.MaxValue)

  def initWs() = Chunk(
    ws.connect,
    ws.connected --> { _ =>
      println("Subscribing to Metrics messages")
      val subscribe: ByteBuffer = Pickle.intoBytes[ClientMessage](ClientMessage.subscribe)
      ws.sendOne(subscribe.arrayBuffer())
    },
    ws.received.map(buf => Unpickle[MetricsMessage].fromBytes(TypedArrayBuffer.wrap(buf))) --> messages,
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
