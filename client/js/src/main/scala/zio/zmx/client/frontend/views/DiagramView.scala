package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._
import zio.zmx.client.MetricsMessage

import zio.zmx.client.frontend.model._
import zio.zmx.client.frontend.state.AppState

/**
 * A DiagramView is implemented as a Laminar element and is responsible for initializing and updating
 * the graph(s) embedded within. It taps into the overall stream of change events, filters out the events
 * that are relevant for the diagram and updates the TimeSeries of the underlying Charts.
 *
 * As we might see a lot of change events, we will throttle the update interval for the graphs as specified in
 * the individual diagram config.
 */
object DiagramView {

  def render(id: String, initial: DiagramConfig, $config: Signal[DiagramConfig]): HtmlElement =
    new DiagramViewImpl($config, AppState.messages.events).render()

  private class DiagramViewImpl(
    $cfg: Signal[DiagramConfig],
    events: EventStream[MetricsMessage]
  ) {

    // A Chart element that will be unitialised and can be inserted into the dom by calling
    // the element() method
    private val chart: ChartView.ChartView = ChartView.ChartView()

    def render(): HtmlElement =
      div(
        child <-- $cfg.map { cfg =>
          div(
            events
              .filter(m => cfg.metric.contains(m.key))
              .throttle(cfg.refresh.toMillis().intValue()) --> Observer[MetricsMessage](onNext = { msg =>
              TimeSeriesEntry.fromMetricsMessage(msg).foreach(chart.recordData)
              chart.update()
            }),
            cls := "bg-gray-900 text-gray-50 rounded my-3 p-3",
            span(
              cls := "text-2xl font-bold my-2",
              s"A diagram for ${cfg.title}"
            ),
            div(
              chart.element()
            )
          )
        }
      )
  }
}
