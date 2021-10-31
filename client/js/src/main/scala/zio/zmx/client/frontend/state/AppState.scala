package zio.zmx.client.frontend.state

import zio._

import com.raquo.laminar.api.L._

import zio.zmx.client.frontend.model._
import zio.zmx.client.frontend.model.MetricSummary._

import zio.metrics.MetricKey

object AppState {

  val connected: Var[Boolean]     = Var(false)
  val shouldConnect: Var[Boolean] = Var(true)

  val dashboardConfig: Var[DashBoardConfig] = Var(DashBoardConfig("ws://localhost:8080/ws", Chunk.empty))

  val counterInfos: Var[Map[MetricKey, CounterInfo]]     = Var(Map.empty)
  val gaugeInfos: Var[Map[MetricKey, GaugeInfo]]         = Var(Map.empty)
  val histogramInfos: Var[Map[MetricKey, HistogramInfo]] = Var(Map.empty)
  val summaryInfos: Var[Map[MetricKey, SummaryInfo]]     = Var(Map.empty)
  val setCountInfos: Var[Map[MetricKey, SetInfo]]        = Var(Map.empty)

  def resetState(): Unit = {
    AppState.shouldConnect.set(false)
    AppState.dashboardConfig.update(_.copy(diagrams = Chunk.empty))
    AppState.counterInfos.set(Map.empty)
    AppState.gaugeInfos.set(Map.empty)
    AppState.histogramInfos.set(Map.empty)
    AppState.summaryInfos.set(Map.empty)
    AppState.setCountInfos.set(Map.empty)
  }
}
