package zio.zmx.client.frontend

import java.nio.ByteBuffer
import scala.scalajs.js.typedarray.ArrayBuffer
import scala.scalajs.js.typedarray.TypedArrayBuffer
import scala.scalajs.js.typedarray.TypedArrayBufferOps._

import com.raquo.laminar.api.L._
import io.laminext.websocket.WebSocket

import AppDataModel._

import zio.Chunk
import zio.zmx.client.frontend.AppDataModel.MetricSummary._

import zio.zmx.client.MetricsMessage
import java.io.PrintWriter
import java.io.StringWriter

import zio.zmx.client.frontend.views.DiagramView
import zio.zmx.client.MetricsMessage._
import scala.scalajs.js.typedarray._

object AppState {

  val diagrams: Var[Chunk[DiagramView]] = Var(Chunk.empty)

  def addCounterDiagram(key: String): Unit   = diagrams.update(_ :+ DiagramView.counterDiagram(key))
  def addGaugeDiagram(key: String): Unit     = diagrams.update(_ :+ DiagramView.gaugeDiagram(key))
  def addHistogramDiagram(key: String): Unit = diagrams.update(_ :+ DiagramView.histogramDiagram(key))
  def addSummaryDiagram(key: String): Unit   = diagrams.update(_ :+ DiagramView.summaryDiagram(key))
  def addSetDiagram(key: String): Unit       = diagrams.update(_ :+ DiagramView.setDiagram(key))

  lazy val messages: EventBus[MetricsMessage] = new EventBus[MetricsMessage]

  lazy val counterMessages: EventStream[CounterChange]     =
    messages.events.collect { case chg: CounterChange => chg }
  lazy val gaugeMessages: EventStream[GaugeChange]         =
    messages.events.collect { case chg: GaugeChange => chg }
  lazy val histogramMessages: EventStream[HistogramChange] =
    messages.events.collect { case chg: HistogramChange => chg }
  lazy val summaryMessages: EventStream[SummaryChange]     =
    messages.events.collect { case chg: SummaryChange => chg }
  lazy val setMessages: EventStream[SetChange]             =
    messages.events.collect { case chg: SetChange => chg }

  val counterInfo: EventStream[CounterInfo]                =
    counterMessages.map(chg => AppDataModel.MetricSummary.fromMessage(chg)).collect {
      case Some(s: MetricSummary.CounterInfo) => s
    }

  val gaugeInfo: EventStream[GaugeInfo] =
    gaugeMessages.map(chg => AppDataModel.MetricSummary.fromMessage(chg)).collect {
      case Some(s: MetricSummary.GaugeInfo) => s
    }

  val histogramInfo: EventStream[HistogramInfo] =
    histogramMessages.map(chg => AppDataModel.MetricSummary.fromMessage(chg)).collect {
      case Some(s: MetricSummary.HistogramInfo) => s
    }

  val summaryInfo: EventStream[SummaryInfo] =
    summaryMessages.map(chg => AppDataModel.MetricSummary.fromMessage(chg)).collect {
      case Some(s: MetricSummary.SummaryInfo) => s
    }

  val setInfo: EventStream[SetInfo] =
    setMessages.map(chg => AppDataModel.MetricSummary.fromMessage(chg)).collect { case Some(s: MetricSummary.SetInfo) =>
      s
    }

  lazy val ws: WebSocket[ArrayBuffer, ArrayBuffer] =
    WebSocket
      .url("ws://devel.wayofquality.de:8080/ws")
      .arraybuffer
      .build(reconnectRetries = Int.MaxValue)

  def initWs() = Chunk(
    ws.connect,
    ws.connected --> { _ =>
      println("Subscribing to Metrics messages")
      val subscribe = byteArray2Int8Array("subscribe".getBytes()).buffer
      ws.sendOne(subscribe)
    },
    ws.received.map { buf =>
      val wrappedBuf = TypedArrayBuffer.wrap(buf)
      wrappedBuf.rewind()
      val wrappedArr = new Array[Byte](wrappedBuf.remaining())
      wrappedBuf.get(wrappedArr)
      new String(wrappedArr)
    } --> { msg =>
      println(msg)
    // messages.emit(msg)
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
