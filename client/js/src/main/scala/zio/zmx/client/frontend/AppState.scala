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

object AppState {

  private val summaries: Var[Map[MetricKey, MetricSummary]] = Var(Map.empty)

  val diagrams: Var[Chunk[HtmlElement]] = Var(Chunk.empty)

  def addDiagram(key: String) = diagrams.update(_ :+ div(span(key)))

  def updateSummary(msg: MetricsMessage) =
    MetricSummary.fromMessage(msg).foreach(summary => summaries.update(_.updated(msg.key, summary)))

  val counterInfo: Signal[Chunk[CounterInfo]]      =
    summaries.signal.map(_.collect { case (_, ci: CounterInfo) => ci }).map(Chunk.fromIterable)

  val gaugeInfo: Signal[Chunk[GaugeInfo]]          =
    summaries.signal.map(_.collect { case (_, ci: GaugeInfo) => ci }).map(Chunk.fromIterable)

  val histogramInfo: Signal[Chunk[HistogramInfo]]  =
    summaries.signal.map(_.collect { case (_, ci: HistogramInfo) => ci }).map(Chunk.fromIterable)

  val summaryInfo: Signal[Chunk[SummaryInfo]]      =
    summaries.signal.map(_.collect { case (_, ci: SummaryInfo) => ci }).map(Chunk.fromIterable)

  val setInfo: Signal[Chunk[SetInfo]]              =
    summaries.signal.map(_.collect { case (_, ci: SetInfo) => ci }).map(Chunk.fromIterable)

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
