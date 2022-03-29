package zio.zmx.client.frontend.state

import scala.scalajs.js.typedarray._

import com.raquo.laminar.api.L._

import zio.Chunk
import zio.metrics.MetricKey
import zio.zmx.client.frontend.components._
import zio.zmx.client.frontend.model._
import zio.zmx.client.frontend.model.Layout._

import io.laminext.websocket.WebSocket

object AppState {

  val wsConnection: Var[Option[WebSocket[ArrayBuffer, ArrayBuffer]]] = Var(None)

  // The theme that is currently used
  val theme: Var[Theme.DaisyTheme] = Var(Theme.DaisyTheme.Wireframe)

  // This reflects whether the app is currently connected, it is set by the
  // WS handler when it has established a connection
  val connected                     = wsConnection.signal.map(maybeWs => maybeWs.isDefined)
  val clientID: Var[Option[String]] = Var(None)

  // This reflects if the user has hit the connect button and we shall try to connect
  // to the configured url
  val shouldConnect: Var[Boolean] = Var(false)

  // The initial WS URL we want to consume events from
  val connectUrl: Var[String] = Var("ws://localhost:8080/ws")

  // The currently displayed diagrams (order is important)
  val dashBoard: Var[Dashboard[PanelConfig]] =
    Var(defaultDashboard)

  // We keep the configuration for the displayed lines in a variable outside the actual display config,
  // so that manipulating the TSConfig does not necessarily trigger an update for the entire dashboard
  val timeSeries: Var[Map[String, Map[TimeSeriesKey, TimeSeriesConfig]]] = Var(Map.empty)

  // Also we keep the recorded data of all displayed panel in the AppState, so that the data won´t
  // be lost on a dashboard re-render
  val recordedData: Var[Map[String, LineChartModel]] = Var(Map.empty)
  val updatedData: EventBus[String]                  = new EventBus[String]

  // The currently available metrics
  val availableMetrics: Var[Set[MetricKey.Untyped]] = Var(Set.empty)

  private def selectedKeys[Type <: MetricKey.Untyped]: Signal[Set[MetricKey.Untyped]] = {
    val pfType: PartialFunction[MetricKey.Untyped, MetricKey.Untyped] = { case k if k.isInstanceOf[Type] => k }
    availableMetrics.signal.changes.map(all => all.collect(pfType)).toSignal(Set.empty)
  }

  // Just some convenience to get all the known metric keys
  val knownCounters: Signal[Set[MetricKey.Untyped]]   = selectedKeys[MetricKey.Counter]
  val knownGauges: Signal[Set[MetricKey.Untyped]]     = selectedKeys[MetricKey.Gauge]
  val knownHistograms: Signal[Set[MetricKey.Untyped]] = selectedKeys[MetricKey.Histogram]
  val knownSummaries: Signal[Set[MetricKey.Untyped]]  = selectedKeys[MetricKey.Summary]
  val knownSetCounts: Signal[Set[MetricKey.Untyped]]  = selectedKeys[MetricKey.Frequency]

  // Reset everything - is usually called upon disconnect
  def resetState(): Unit = {
    clientID.set(None)
    shouldConnect.set(false)
    dashBoard.set(defaultDashboard)
    recordedData.set(Map.empty)
    timeSeries.set(Map.empty)
    availableMetrics.set(Set.empty)
  }

  lazy val defaultDashboard: Dashboard[PanelConfig] = {

    val panel: String => Dashboard[PanelConfig] = s => Dashboard.Cell(PanelConfig.EmptyConfig.create(s))

    panel("ZMX Dashboard")
  }
}
