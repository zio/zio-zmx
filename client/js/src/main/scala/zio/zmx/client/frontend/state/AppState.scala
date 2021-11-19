package zio.zmx.client.frontend.state

import zio.Chunk
import scala.scalajs.js.typedarray._

import com.raquo.laminar.api.L._
import io.laminext.websocket.WebSocket

import zio.zmx.client.MetricsMessage
import zio.zmx.client.frontend.model._
import zio.zmx.client.frontend.model.Layout._
import zio.zmx.client.frontend.components._

import zio.metrics.MetricKey

object AppState {

  val wsConnection: Var[Option[WebSocket[ArrayBuffer, ArrayBuffer]]] = Var(None)

  // The theme that is currently used
  val theme: Var[Theme.DaisyTheme] = Var(Theme.DaisyTheme.Luxury)

  // This reflects whether the app is currently connected, it is set by the
  // WS handler when it has established a connection
  val connected = wsConnection.signal.map(maybeWs => maybeWs.isDefined)

  // This reflects if the user has hit the connect button and we shall try to connect
  // to the configured url
  val shouldConnect: Var[Boolean] = Var(false)

  // When we do have a WS connection this is our source of events
  // TODO: We would like to make the underlying protocol more efficient
  val messages: EventBus[MetricsMessage] = new EventBus[MetricsMessage]

  // The initial WS URL we want to consume events from
  val connectUrl: Var[String] = Var("ws://localhost:8080/ws")

  // The currently displayed diagrams (order is important)
  val dashBoard: Var[Dashboard[PanelConfig]] =
    Var(defaultDashboard)

  val metricInfos: Var[Map[MetricKey, MetricInfo]] = Var(Map.empty)

  private def selectedInfos(f: PartialFunction[(MetricKey, MetricInfo), MetricInfo]): Signal[Chunk[MetricInfo]] =
    metricInfos.signal.changes.map(all => Chunk.fromIterable(all.collect(f))).toSignal(Chunk.empty)

  val counterInfos: Signal[Chunk[MetricInfo]]   = selectedInfos { case (_: MetricKey.Counter, info) => info }
  val gaugeInfos: Signal[Chunk[MetricInfo]]     = selectedInfos { case (_: MetricKey.Gauge, info) => info }
  val histogramInfos: Signal[Chunk[MetricInfo]] = selectedInfos { case (_: MetricKey.Histogram, info) => info }
  val summaryInfos: Signal[Chunk[MetricInfo]]   = selectedInfos { case (_: MetricKey.Summary, info) => info }
  val setCountInfos: Signal[Chunk[MetricInfo]]  = selectedInfos { case (_: MetricKey.SetCount, info) => info }

  // Reset everything - is usually called upon disconnect
  def resetState(): Unit = {
    shouldConnect.set(false)
    dashBoard.set(defaultDashboard)
    metricInfos.set(Map.empty)
  }

  private lazy val defaultDashboard: Dashboard[PanelConfig] = {

    val panel: String => Dashboard[PanelConfig] = s => Dashboard.Cell(PanelConfig.EmptyConfig.create(s))

    panel("ZMX Dashboard")

  }
}
