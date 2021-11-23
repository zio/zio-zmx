package zio.zmx.client.frontend.views

import scala.scalajs.js
import js.JSConverters._

import com.raquo.laminar.api.L._

import zio.zmx.client.frontend.model._
import zio.zmx.client.frontend.model.PanelConfig._

import zio.zmx.client.frontend.state.AppState
import zio.zmx.client.MetricsMessage
import zio.metrics.MetricKey

import zio.zmx.client.frontend.d3v7.d3

object LineChartView {

  def render($cfg: Signal[DisplayConfig]): HtmlElement =
    new ChartViewImpl().render($cfg)

  private class ChartViewImpl() {

    // This is the map of "lines" displayed within the chart.
    private val data: Var[LineChartModel] = Var(LineChartModel(100))

    // Add a new Timeseries, only if the graph does not contain a line for the key yet
    // The key is the string representation of a metrickey, in the case of histograms, summaries and setcounts
    // it identifies a single stream of samples within the collection of the metric
    def recordData(entry: TimeSeriesEntry): Unit =
      data.update(_.recordEntry(entry))

    private def update(cfg: DisplayConfig): Unit = {
      val start = System.currentTimeMillis()

      val sel = d3
        .select(s"#${chartId(cfg)}")

      val d3Data = data.now().snapshot.values.flatten
      val gData  = d3Data.toJSArray
      println(d3Data.size)

      val foo: TimeSeriesEntry => String = v => {
        println(s"Generating line from [$v]")
        d3.line()(js.Array(js.Tuple2(v.when.toEpochMilli().doubleValue(), v.value)))
      }

      val _ = sel
        .select("svg")
        .selectAll("path")
        .data(gData)
        .enter()
        .append("path")
        .attr("fill", "none")
        .attr("stroke", "#dd6677")
        .attr("stroke-width", 1.5)
        .attr("d", foo)
        .data(gData)
        .exit()

      val dur = System.currentTimeMillis() - start
      println(s"Update for diagram took $dur ms")
    }

    val metricStream: (DisplayConfig, MetricKey) => EventSource[MetricsMessage] = (cfg, m) =>
      AppState.metricMessages.now().get(m) match {
        case None    => EventStream.empty
        case Some(s) => s.events.throttle(cfg.refresh.toMillis().intValue())
      }

    def updateFromMetricsStream(cfg: DisplayConfig) = {
      data.update(_.updateMaxSamples(cfg.maxSamples))
      cfg.metrics.map(m =>
        metricStream(cfg, m) --> Observer[MetricsMessage] { msg =>
          TimeSeriesEntry.fromMetricsMessage(msg).foreach(recordData)
          update(cfg)
        }
      )
    }

    private val chartId: DisplayConfig => String = cfg => s"chart-${cfg.id}"

    def render($cfg: Signal[DisplayConfig]) =
      div(
        styleAttr := "width: 95%; height: 95%; top: 2.5%; left: 2.5%",
        cls := "border-2 border-accent rounded-lg absolute",
        idAttr <-- $cfg.map(chartId),
        children <-- $cfg.map { cfg =>
          Seq(
            div(
              display := "none",
              updateFromMetricsStream(cfg)
            ),
            div(
              cls := "absolute w-full h-full",
              svg.svg(
                svg.cls := "absolute h-full w-full",
                svg.viewBox("0 0 1000 1000"),
                svg.preserveAspectRatio("none")
              )
            )
          )
        }
      )

  }

}
