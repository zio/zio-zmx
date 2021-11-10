package zio.zmx.client.frontend.state

import zio._

import com.raquo.laminar.api.L._

import zio.zmx.client.MetricsMessage
import zio.zmx.client.frontend.model._
import zio.zmx.client.frontend.model.MetricSummary._

import zio.metrics.MetricKey

object AppState {

  val connected: Var[Boolean]     = Var(false)
  val shouldConnect: Var[Boolean] = Var(true)

  val messages: EventBus[MetricsMessage] = new EventBus[MetricsMessage]

  val connectUrl: Var[String]             = Var("ws://localhost:8080/ws")
  val diagrams: Var[Chunk[DiagramConfig]] = Var(Chunk.empty)

  val counterInfos: Var[Map[MetricKey, CounterInfo]]     = Var(Map.empty)
  val gaugeInfos: Var[Map[MetricKey, GaugeInfo]]         = Var(Map.empty)
  val histogramInfos: Var[Map[MetricKey, HistogramInfo]] = Var(Map.empty)
  val summaryInfos: Var[Map[MetricKey, SummaryInfo]]     = Var(Map.empty)
  val setCountInfos: Var[Map[MetricKey, SetInfo]]        = Var(Map.empty)

  def resetState(): Unit = {
    shouldConnect.set(false)
    diagrams.set(Chunk.empty)
    counterInfos.set(Map.empty)
    gaugeInfos.set(Map.empty)
    histogramInfos.set(Map.empty)
    summaryInfos.set(Map.empty)
    setCountInfos.set(Map.empty)
  }
}
