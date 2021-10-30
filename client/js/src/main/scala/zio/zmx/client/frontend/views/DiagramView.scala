package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._
import org.scalajs.dom.ext.Color
import zio.zmx.client.MetricsMessage
import zio.zmx.client.frontend.state.AppState
import scala.util.Random
import zio._
import zio.metrics._

import zio.zmx.client.frontend.model.TimeSeriesEntry
import zio.zmx.client.frontend.utils.Implicits._

/**
 * A DiagramView is implemented as a Laminar element and is responsible for initialising and updating
 * the graph(s) embedded within. It taps into the overall stream of change events, filters out the events
 * that are relevant for the diagram and updates the TimeSeries of the underlying Charts.
 *
 * As we might see a lot of change events, we will throttle the update interval for the graphs (currently hard coded to 5 seconds.)
 */
sealed trait DiagramView {
  def render(): HtmlElement
}

object DiagramView {

  def createDiagram(key: MetricKey): DiagramView =
    new DiagramViewImpl(key, AppState.messages.events, 5.seconds)

  private class DiagramViewImpl(key: MetricKey, events: EventStream[MetricsMessage], interval: Duration)
      extends DiagramView {

    private val rnd                        = new Random()
    private val chart: ChartView.ChartView = ChartView.ChartView()
    private def nextColor(): Color         = Color(rnd.nextInt(240), rnd.nextInt(240), rnd.nextInt(240))

    override def render(): HtmlElement =
      div(
        events
          .filter(m => m.key.equals(key))
          .throttle(interval.toMillis().intValue()) --> Observer[MetricsMessage](onNext = { msg =>
          TimeSeriesEntry.fromMetricsMessage(msg).foreach { entry =>
            chart.addTimeseries(entry.key, nextColor(), 0.5)
            chart.recordData(entry)
          }
        }),
        cls := "bg-gray-900 text-gray-50 rounded my-3 p-3",
        span(
          cls := "text-2xl font-bold my-2",
          s"A diagram for ${key.longName}"
        ),
        div(
          chart.element()
        )
      )
  }
}
