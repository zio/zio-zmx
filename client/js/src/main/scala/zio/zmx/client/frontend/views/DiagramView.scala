package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._
import org.scalajs.dom.ext.Color
import zio.zmx.client.MetricsMessage
import zio.zmx.client.frontend.AppState
import zio.duration._
import zio.zmx.client.frontend.AppDataModel
import zio.zmx.client.MetricsMessage._
import zio.zmx.internal.MetricKey
import zio.zmx.state.MetricType
import scala.util.Random

sealed trait DiagramView {
  def render(): HtmlElement
}

object DiagramView {

  def counterDiagram(key: String): DiagramView =
    new DiagramViewImpl(key, AppState.messages.events, 5.seconds)

  def gaugeDiagram(key: String): DiagramView =
    new DiagramViewImpl(key, AppState.messages.events, 5.seconds)

  def histogramDiagram(key: String): DiagramView =
    new DiagramViewImpl(key, AppState.messages.events, 5.seconds)

  def summaryDiagram(key: String): DiagramView =
    new DiagramViewImpl(key, AppState.messages.events, 5.seconds)

  def setDiagram(key: String): DiagramView =
    new DiagramViewImpl(key, AppState.messages.events, 5.seconds)

  private def getKey(m: MetricKey): String = m match {
    case key: MetricKey.Gauge     => key.name + AppDataModel.MetricSummary.labels(key.tags)
    case key: MetricKey.Counter   => key.name + AppDataModel.MetricSummary.labels(key.tags)
    case key: MetricKey.Histogram => key.name + AppDataModel.MetricSummary.labels(key.tags)
    case key: MetricKey.Summary   => key.name + AppDataModel.MetricSummary.labels(key.tags)
    case key: MetricKey.SetCount  => key.name + AppDataModel.MetricSummary.labels(key.tags)
  }

  private class DiagramViewImpl(key: String, events: EventStream[MetricsMessage], interval: Duration)
      extends DiagramView {

    private val rnd                        = new Random()
    private val chart: ChartView.ChartView = ChartView.ChartView()
    private def nextColor(): Color         = Color(rnd.nextInt(240), rnd.nextInt(240), rnd.nextInt(240))

    override def render(): HtmlElement =
      div(
        events
          .filter(m => getKey(m.key).equals(key))
          .throttle(interval.toMillis().intValue()) --> Observer[MetricsMessage](onNext = {
          case CounterChange(m, when, absValue, _) =>
            val key = getKey(m)
            chart.addTimeseries(key, nextColor())
            chart.recordData(key, when, absValue)
          case GaugeChange(m, when, absValue, _)   =>
            val key = getKey(m)
            chart.addTimeseries(key, nextColor(), 0.5)
            chart.recordData(key, when, absValue)
          case SummaryChange(key, when, state)     =>
            state.details match {
              case MetricType.Summary(_, quantiles, count, sum) =>
                val sumKey = getKey(key)
                quantiles.foreach { case (q, v) =>
                  val tsKey = sumKey + s" - q=$q"
                  chart.addTimeseries(tsKey, nextColor(), tension = 0.5)
                  v.foreach(chart.recordData(tsKey, when, _))
                }
                val avgKey = sumKey + " - avg"
                chart.addTimeseries(avgKey, nextColor(), tension = 0.5)
                chart.recordData(avgKey, when, sum / count)
              case _                                            => ()
            }
          case HistogramChange(key, when, state)   =>
            state.details match {
              case MetricType.DoubleHistogram(buckets, count, sum) =>
                val histKey = getKey(key)
                buckets.foreach { case (le, count) =>
                  val bKey = histKey + s" - le=$le"
                  chart.addTimeseries(bKey, nextColor(), tension = 0.5)
                  chart.recordData(bKey, when, count.doubleValue())
                }
                val avgKey  = histKey + " - avg"
                chart.addTimeseries(avgKey, nextColor(), tension = 0.5)
                chart.recordData(avgKey, when, sum / count)
              case _                                               => ()
            }
          case SetChange(key, when, state)         =>
            state.details match {
              case MetricType.SetCount(setTag, occurrences) =>
                val setKey = getKey(key)
                occurrences.foreach { case (tag, count) =>
                  val sKey = setKey + s" - $setTag=$tag"
                  chart.addTimeseries(sKey, nextColor(), tension = 0.5)
                  chart.recordData(sKey, when, count.doubleValue())
                }
              case _                                        => ()
            }
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
