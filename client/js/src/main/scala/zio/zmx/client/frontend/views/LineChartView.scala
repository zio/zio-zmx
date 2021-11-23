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

import zio.zmx.client.frontend.utils.Implicits._

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

      def svgGraph = d3
        .select(s"#${chartId(cfg)}")
        .select("svg")

      def remove(what: String*) = {

        what.foreach { s =>
          val _ = svgGraph
            .selectAll(s)
            .data(js.Array[TimeSeriesEntry]())
            .exit()
            .remove()
        }

        d3
          .select(s"#${chartId(cfg)}")
          .select("svg")
      }

      val start = System.currentTimeMillis()

      val totalWidth: Int  = 1000
      val totalHeight: Int = 1000

      val marginLeft: Int   = 50
      val marginRight: Int  = 30
      val marginTop: Int    = 30
      val marginBottom: Int = 30

      val chartWidth: Int  = totalWidth - marginLeft - marginRight
      val chartHeight: Int = totalHeight - marginBottom - marginTop

      val d3Data: Iterable[TimeSeriesEntry] = data.now().snapshot.values.flatten

      val (minDate, maxDate): (Instant, Instant) = {
        val inst = d3Data.map(_.when)
        (inst.min, inst.max)
      }

      val (minVal, maxVal): (Double, Double) = {
        val vals = d3Data.map(_.value)
        (vals.min, vals.max)
      }

      val xScale: TimeScale = d3
        .scaleTime()
        .domain(
          js.Array(minDate.toJSDate, maxDate.toJSDate)
        )
        .range(
          js.Array(0d, chartWidth.doubleValue())
        )
        .nice()

      val yScale: LinearScale = d3
        .scaleLinear()
        .domain(js.Array(minVal, maxVal))
        .range(js.Array(chartHeight.doubleValue(), 0d))
        .nice()

      val _ = remove(".zmxLine", ".zmxAxes")
        .attr("viewBox", s"-${marginLeft} -${marginTop} $totalWidth $totalHeight")
        .attr("preserveAspectRatio", "none")
        .append("g")
        .attr("class", "zmxAxes")
        .attr("transform", s"translate(0, $chartHeight)")
        .call(d3.axisBottom(xScale))

      val _ = svgGraph
        .append("g")
        .attr("class", "zmxAxes")
        .call(d3.axisLeft(yScale))
        .append("path")
        .attr("class", "zmxLine")
        .attr("fill", "none")
        .attr("stroke", "#dd6677")
        .attr("stroke-width", 1.5)
        .attr(
          "d",
          d3.line()(
            d3Data.map { e =>
              val x: Double = xScale(e.when.toJSDate)
              val y: Double = yScale(e.value)
              js.Tuple2(x, y)
            }.toJSArray
          )
        )

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
