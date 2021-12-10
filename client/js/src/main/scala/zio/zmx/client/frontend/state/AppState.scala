package zio.zmx.client.frontend.state

import zio.Chunk
import scala.scalajs.js.typedarray._

import com.raquo.laminar.api.L._
import io.laminext.websocket.WebSocket

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
  // so that manipulating the TSConfig doese not necessarily trigger an update for the entire dashboard
  val timeSeries: Var[Map[String, Map[TimeSeriesKey, TimeSeriesConfig]]] = Var(Map.empty)

  // Also we keep the recorded data of all displayed panel in the AppState, so that the data won´t
  // be lost on a dashboard re-render
  val recordedData: Var[Map[String, LineChartModel]] = Var(Map.empty)
  val updatedData: EventBus[String]                  = new EventBus[String]

  // The currently available metrics
  val availableMetrics: Var[Chunk[MetricKey]] = Var(Chunk.empty)

  private def selectedKeys(f: PartialFunction[MetricKey, MetricKey]): Signal[Chunk[MetricKey]] =
    availableMetrics.signal.changes.map(all => Chunk.fromIterable(all.collect(f))).toSignal(Chunk.empty)

  // Just some convenience to get all the known metric keys
  val knownCounters: Signal[Chunk[MetricKey]]   = selectedKeys { case (k: MetricKey.Counter) => k }
  val knownGauges: Signal[Chunk[MetricKey]]     = selectedKeys { case (k: MetricKey.Gauge) => k }
  val knownHistograms: Signal[Chunk[MetricKey]] = selectedKeys { case (k: MetricKey.Histogram) => k }
  val knownSummaries: Signal[Chunk[MetricKey]]  = selectedKeys { case (k: MetricKey.Summary) => k }
  val knownSetCounts: Signal[Chunk[MetricKey]]  = selectedKeys { case (k: MetricKey.SetCount) => k }

  // Reset everything - is usually called upon disconnect
  def resetState(): Unit = {
    clientID.set(None)
    shouldConnect.set(false)
    dashBoard.set(defaultDashboard)
    recordedData.set(Map.empty)
    timeSeries.set(Map.empty)
    availableMetrics.set(Chunk.empty)
  }

  lazy val defaultDashboard: Dashboard[PanelConfig] = {

    val panel: String => Dashboard[PanelConfig] = s => Dashboard.Cell(PanelConfig.EmptyConfig.create(s))

    panel("ZMX Dashboard")
  }
}
