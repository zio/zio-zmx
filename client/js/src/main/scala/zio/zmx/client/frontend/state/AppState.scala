package zio.zmx.client.frontend.state

import scala.scalajs.js.typedarray.ArrayBuffer
import scala.scalajs.js.typedarray.TypedArrayBuffer

import com.raquo.laminar.api.L._
import io.laminext.websocket.WebSocket

import zio._
import zio.zmx.client.frontend.model.MetricSummary
import zio.zmx.client.frontend.model.MetricSummary._

import java.io.PrintWriter
import java.io.StringWriter

import zio.zmx.client.MetricsMessage._
import scala.scalajs.js.typedarray._

import upickle.default._

import zio.zmx.client.MetricsMessage
import zio.zmx.client.frontend.model.DiagramConfig

object AppState {
  val diagramConfigs: Var[Chunk[DiagramConfig]] = Var(Chunk.empty)

  def addDiagram(cfg: DiagramConfig) =
    diagramConfigs.update(_ :+ cfg)

  // The overall stream of incoming metric updates
  lazy val messages: EventBus[MetricsMessage] = new EventBus[MetricsMessage]

  // The stream of metric updates specifically for each known metric type
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

  // Streams of events specific for the metric types mapped to info objects, so that we can feed the
  // stream of info objects into our table views
  val counterInfo: EventStream[CounterInfo]                =
    counterMessages.map(chg => MetricSummary.fromMessage(chg)).collect { case Some(s: MetricSummary.CounterInfo) =>
      s
    }

  val gaugeInfo: EventStream[GaugeInfo] =
    gaugeMessages.map(chg => MetricSummary.fromMessage(chg)).collect { case Some(s: MetricSummary.GaugeInfo) =>
      s
    }

  val histogramInfo: EventStream[HistogramInfo] =
    histogramMessages.map(chg => MetricSummary.fromMessage(chg)).collect { case Some(s: MetricSummary.HistogramInfo) =>
      s
    }

  val summaryInfo: EventStream[SummaryInfo] =
    summaryMessages.map(chg => MetricSummary.fromMessage(chg)).collect { case Some(s: MetricSummary.SummaryInfo) =>
      s
    }

  val setInfo: EventStream[SetInfo] =
    setMessages.map(chg => MetricSummary.fromMessage(chg)).collect { case Some(s: MetricSummary.SetInfo) =>
      s
    }

  lazy val ws: WebSocket[ArrayBuffer, ArrayBuffer] =
    WebSocket
      .url("ws://localhost:8080/ws")
      .arraybuffer
      .build(reconnectRetries = Int.MaxValue)

  def initWs() = Chunk(
    ws.connect,
    // Initially send a "subscribe" message to kick off the stream of metric updates via Web Sockets
    ws.connected --> { _ =>
      println("Subscribing to Metrics messages")
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
      messages.emit(change)
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
