package zio.zmx.client.frontend.state

import scala.scalajs.js.typedarray._

import zio._

import com.raquo.laminar.api.L._
import io.laminext.websocket.WebSocket

import zio.zmx.client.MetricsMessage
import zio.zmx.client.frontend.model._
import zio.zmx.client.frontend.model.Layout._
import zio.zmx.client.frontend.model.MetricSummary._
import zio.zmx.client.frontend.components._

import zio.metrics.MetricKey

object AppState {

  val wsConnection: Var[Option[WebSocket[ArrayBuffer, ArrayBuffer]]] = Var(None)

  // The theme that is currently used
  val theme: Var[Theme.DaisyTheme] = Var(Theme.DaisyTheme.Halloween)

  // This reflects whether the app is currently connected, it is set by the
  // WS handler when it has established a connection
  val connected = wsConnection.signal.map(maybeWs => maybeWs.isDefined)

  // This reflects if the user has hit the connect button and we shall try to connect
  // to the configured url
  val shouldConnect: Var[Boolean] = Var(false)

  // When we do have a WS connection this is our source of events
  // TODO: We would like to make the underlying protocol more efficient
  val messages: EventBus[MetricsMessage] = new EventBus[MetricsMessage]

  // The WS URL we want to consume events from
  val connectUrl: Var[String] = Var("ws://localhost:8080/ws")

  // The currently displayed diagrams (order is important)
  val dashBoard: Var[Dashboard[PanelConfig]] =
    Var(defaultDashboard)

  val counterInfos: Var[Map[MetricKey, CounterInfo]]     = Var(Map.empty)
  val gaugeInfos: Var[Map[MetricKey, GaugeInfo]]         = Var(Map.empty)
  val histogramInfos: Var[Map[MetricKey, HistogramInfo]] = Var(Map.empty)
  val summaryInfos: Var[Map[MetricKey, SummaryInfo]]     = Var(Map.empty)
  val setCountInfos: Var[Map[MetricKey, SetInfo]]        = Var(Map.empty)

  // Reset everything - is usually called upon disconnect
  def resetState(): Unit = {
    shouldConnect.set(false)
    dashBoard.set(defaultDashboard)
    counterInfos.set(Map.empty)
    gaugeInfos.set(Map.empty)
    histogramInfos.set(Map.empty)
    summaryInfos.set(Map.empty)
    setCountInfos.set(Map.empty)
  }

  private lazy val defaultDashboard: Dashboard[PanelConfig] = {

    val panel: String => Dashboard[PanelConfig] = s => Dashboard.Cell(PanelConfig.EmptyPanel.create(s))

    Dashboard.VGroup(
      Chunk(
        Dashboard.HGroup(
          Chunk(
            Dashboard.VGroup(Chunk(panel("P1"), panel("P2"))),
            Dashboard.HGroup(Chunk(panel("P3"), panel("3a")))
          )
        ),
        panel("P4")
      )
    )
  }
}
