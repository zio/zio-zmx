package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._
import zio.zmx.client.MetricsMessage
import zio.zmx.client.frontend.AppState
import zio.duration._
import zio.zmx.client.frontend.AppDataModel
import zio.zmx.client.MetricsMessage.CounterChange
import zio.zmx.client.MetricsMessage.GaugeChange
import zio.zmx.client.MetricsMessage.HistogramChange
import zio.zmx.client.MetricsMessage.SummaryChange
import zio.zmx.client.MetricsMessage.SetChange
import zio.zmx.internal.MetricKey
sealed trait DiagramView {
  def render(): HtmlElement
}

object DiagramView {

  def counterDiagram(key: String): DiagramView =
    new DiagramViewImpl[MetricsMessage.CounterChange](key, AppState.counterMessages, 5.seconds) {
      val chart: ChartView.ChartView = ChartView.ChartView()
    }

  def gaugeDiagram(key: String): DiagramView =
    new DiagramViewImpl[MetricsMessage.GaugeChange](key, AppState.gaugeMessages, 5.seconds) {
      val chart: ChartView.ChartView = ChartView.ChartView()
    }

  def histogramDiagram(key: String): DiagramView =
    new DiagramViewImpl[MetricsMessage.HistogramChange](key, AppState.histogramMessages, 5.seconds) {
      val chart: ChartView.ChartView = ???
    }

  def summaryDiagram(key: String): DiagramView =
    new DiagramViewImpl[MetricsMessage.SummaryChange](key, AppState.summaryMessages, 5.seconds) {
      val chart: ChartView.ChartView = ???
    }

  def setDiagram(key: String): DiagramView =
    new DiagramViewImpl[MetricsMessage.SetChange](key, AppState.setMessages, 5.seconds) {
      val chart: ChartView.ChartView = ???
    }

  private def getKey(m: MetricKey): String = m match {
    case key: MetricKey.Gauge     => key.name + AppDataModel.MetricSummary.labels(key.tags)
    case key: MetricKey.Counter   => key.name + AppDataModel.MetricSummary.labels(key.tags)
    case key: MetricKey.Histogram => key.name + AppDataModel.MetricSummary.labels(key.tags)
    case key: MetricKey.Summary   => key.name + AppDataModel.MetricSummary.labels(key.tags)
    case key: MetricKey.SetCount  => key.name + AppDataModel.MetricSummary.labels(key.tags)
  }

  abstract private class DiagramViewImpl[M <: MetricsMessage](key: String, events: EventStream[M], interval: Duration)
      extends DiagramView {

    val chart: ChartView.ChartView

    override def render(): HtmlElement =
      div(
        events
          .filter(m => getKey(m.key).equals(key))
          .throttle(interval.toMillis().intValue()) --> Observer[M](onNext = {
          case CounterChange(m, when, absValue, _) =>
            val key = getKey(m)
            chart.addTimeseries(key, "#00dd00")
            chart.recordData(key, when, absValue)
          case GaugeChange(m, when, absValue, _)   =>
            val key = getKey(m)
            chart.addTimeseries(key, "#dd0000", 0.5)
            chart.recordData(key, when, absValue)
          case _                                   => ()
        }),
        cls := "bg-gray-900 text-gray-50 rounded my-3 p-3",
        span(
          cls := "text-2xl font-bold my-2",
          s"A diagram for $key"
        ),
        div(
          chart.element()
        )
      )
  }
}
