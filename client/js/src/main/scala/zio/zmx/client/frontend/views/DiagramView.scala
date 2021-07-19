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
sealed trait DiagramView {
  def render(): HtmlElement
}

object DiagramView {

  def counterDiagram(key: String): DiagramView =
    new DiagramViewImpl[MetricsMessage.CounterChange](key, AppState.counterMessages, 5.seconds) {
      val chart: ChartView.ChartView = {
        val c = new ChartView.ChartView()
        c.addTimeseries(key, "#00dd00")
        c
      }
    }

  def gaugeDiagram(key: String): DiagramView =
    new DiagramViewImpl[MetricsMessage.GaugeChange](key, AppState.gaugeMessages, 5.seconds) {
      val chart: ChartView.ChartView = ???
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

  private def getKey(m: MetricsMessage): String = m match {
    case GaugeChange(key, _, _, _)   => key.name + AppDataModel.MetricSummary.labels(key.tags)
    case CounterChange(key, _, _, _) => key.name + AppDataModel.MetricSummary.labels(key.tags)
    case HistogramChange(key, _, _)  => key.name + AppDataModel.MetricSummary.labels(key.tags)
    case SummaryChange(key, _, _)    => key.name + AppDataModel.MetricSummary.labels(key.tags)
    case SetChange(key, _, _)        => key.name + AppDataModel.MetricSummary.labels(key.tags)
  }

  private abstract class DiagramViewImpl[M <: MetricsMessage](key: String, events: EventStream[M], interval: Duration)
      extends DiagramView {

    val chart: ChartView.ChartView

    override def render(): HtmlElement =
      div(
        events.filter(m => getKey(m).equals(key)).throttle(interval.toMillis().intValue()) --> Observer[M](onNext =
          m =>
            m match {
              case CounterChange(_, when, absValue, _) =>
                val key = getKey(m)
                chart.addTimeseries(key, "#00dd00")
                chart.recordData(getKey(m), when, absValue)
              case _                                   => ()
            }
        ),
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
