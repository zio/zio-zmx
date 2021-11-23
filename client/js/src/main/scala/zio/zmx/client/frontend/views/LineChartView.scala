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
import zio.zmx.client.frontend.d3v7.d3Scale._
import java.time.Instant

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

      val width: Int  = 1000
      val height: Int = 1000

      val d3Data: Iterable[TimeSeriesEntry] = data.now().snapshot.values.flatten

      val (minDate, maxDate): (Instant, Instant) = {
        val inst = d3Data.map(_.when)
        (inst.min, inst.max)
      }

      val (minVal, maxVal): (Double, Double) = {
        val vals = d3Data.map(_.value)
        (vals.min, vals.max)
      }

      println((minDate, maxDate, minVal, maxVal))

      val xScale: Scale = d3
        .scaleTime()
        .domain(
          js.Array(
            new js.Date(minDate.toEpochMilli().doubleValue()),
            new js.Date(maxDate.toEpochMilli().doubleValue())
          )
        )
        .range(
          js.Array(0d, width.doubleValue())
        )

      val yScale: Scale = d3
        .scaleLinear()
        .domain(js.Array(minVal, maxVal))
        .range(js.Array(0d, height.doubleValue()))

      val chart = d3
        .select(s"#${chartId(cfg)}")
        .select("svg")
        .attr("viewBox", s"0 0 $width $height")
        .attr("preserveAspectRatio", "none")
        .call(d3.axisBottom(xScale))
        .call(d3.axisLeft(yScale))

      val gData = d3Data.toJSArray
      println(d3Data.size)

      val foo: TimeSeriesEntry => String = v => {
        println(s"Generating line from [$v]")
        d3.line()(js.Array(js.Tuple2(v.when.toEpochMilli().doubleValue(), v.value)))
      }

      val fooId: TimeSeriesEntry => String = e => {
        println(s"Checking node for [$e]")
        Option(e).map(e => s"${e.key.key}-${e.when.toEpochMilli()}").getOrElse("")
      }

      val selected = chart
        .selectAll("path")
        .data(gData, fooId)

      val _ = selected
        .enter()
        .append("path")
        .attr("fill", "none")
        .attr("stroke", "#dd6677")
        .attr("stroke-width", 1.5)
        .attr("d", foo)

      val _ = selected.exit().remove()

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
                svg.cls := "absolute h-full w-full"
              )
            )
          )
        }
      )

  }

}
