package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._

import zio.zmx.client.frontend.model._
import zio.zmx.client.frontend.model.PanelConfig._

import zio.zmx.client.frontend.state.AppState
import zio.zmx.client.MetricsMessage
import zio.metrics.MetricKey

import zio.zmx.client.frontend.d3v7.d3

object LineChartView {

  def render($cfg: Signal[DisplayConfig]): HtmlElement =
    new ChartViewImpl($cfg).render()

  private class ChartViewImpl($cfg: Signal[DisplayConfig]) {

    // This is the map of "lines" displayed within the chart.
    private val data: Var[LineChartModel] = Var(LineChartModel(100))

    // Add a new Timeseries, only if the graph does not contain a line for the key yet
    // The key is the string representation of a metrickey, in the case of histograms, summaries and setcounts
    // it identifies a single stream of samples within the collection of the metric
    def recordData(entry: TimeSeriesEntry): Unit =
      data.update(_.recordEntry(entry))

    // private def mount(canvas: ReactiveHtmlElement[Canvas]): Unit =
    //   chart = Some(new Chart(canvas.ref, options))

    private def update(): Unit = {
      val start = System.currentTimeMillis()
      println(data.now().snapshot)
      val dur   = System.currentTimeMillis() - start
      println(s"Update for diagram took $dur ms")
    }

    val metricStream: (DisplayConfig, MetricKey) => EventSource[MetricsMessage] = (cfg, m) =>
      AppState.metricMessages.now().get(m) match {
        case None    => EventStream.empty
        case Some(s) => s.events.throttle(cfg.refresh.toMillis().intValue())
      }

    def updateFromMetricsStream(cfg: DisplayConfig) = cfg.metrics.map(m =>
      metricStream(cfg, m) --> Observer[MetricsMessage] { msg =>
        println(s"Received $msg")
        TimeSeriesEntry.fromMetricsMessage(msg).foreach(recordData)
        update()
      }
    )

    private def chartId: DisplayConfig => String = cfg => s"chart-${cfg.id}"

    def d3View(cfg: DisplayConfig) =
      div(
        idAttr := chartId(cfg),
        styleAttr := "width: 90%; height: 90%;",
        cls := "border-2 border-red-500 rounded-lg place-self-center m-auto",
        updateFromMetricsStream(cfg),
        svg.svg(
          svg.cls := "w-full h-full",
          //svg.viewBox("0 0 100 100"),
          svg.preserveAspectRatio("none")
        ),
        onMountCallback { _ =>
          val _ = d3
            .select(s"#${chartId(cfg)}")
            .select("svg")
            .append("line")
            .attr("x1", 0)
            .attr("x2", "100%")
            .attr("y1", 0)
            .attr("y2", "100%")
            .attr("stroke-width", 1)
            .attr("stroke", "red")
        }
      )

    def render(): HtmlElement =
      div(
        cls := "flex flex-grow",
        child <-- $cfg.map(d3View)
      )
  }

}
