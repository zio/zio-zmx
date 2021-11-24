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
import zio.zmx.client.frontend.d3v7.d3Selection._

import zio.zmx.client.frontend.d3v7._
import zio.zmx.client.frontend.utils.DomUtils
import scala.util.Random
import org.scalajs.dom
import zio.zmx.client.frontend.state.Command

object LineChartView {

  def render($cfg: Signal[DisplayConfig]): HtmlElement =
    new ChartViewImpl().render($cfg)

  private class ChartViewImpl() {

    // Add a new Timeseries, only if the graph does not contain a line for the key yet
    // The key is the string representation of a metrickey, in the case of histograms, summaries and setcounts
    // it identifies a single stream of samples within the collection of the metric
    def recordData(cfg: DisplayConfig, entry: TimeSeriesEntry): Unit =
      Command.observer.onNext(Command.RecordPanelData(cfg, entry))

    private val rnd = new Random()
    def nextColor   = DomUtils.Color(
      rnd.nextInt(210) + 30,
      rnd.nextInt(210) + 30,
      rnd.nextInt(210) + 30
    )

    private def update(cfg: DisplayConfig): Unit = {

      val start = System.currentTimeMillis()

      val totalWidth: Int  = 1000
      val totalHeight: Int = 1000

      val marginLeft: Int   = 50
      val marginRight: Int  = 30
      val marginTop: Int    = 30
      val marginBottom: Int = 30

      val chartWidth: Int  = totalWidth - marginLeft - marginRight
      val chartHeight: Int = totalHeight - marginBottom - marginTop

      val d3Data: LineChartModel = AppState.recordedData.now().getOrElse(cfg.id, LineChartModel(cfg.maxSamples))

      val xScale: TimeScale = d3
        .scaleTime()
        .domain(
          js.Array(d3Data.minTime, d3Data.maxTime)
        )
        .range(
          js.Array(0d, chartWidth.doubleValue())
        )
        .nice()

      val yScale: LinearScale = d3
        .scaleLinear()
        .domain(js.Array(d3Data.minValue, d3Data.maxValue))
        .range(js.Array(chartHeight.doubleValue(), 0d))
        .nice()

      def addLines(sel: Selection[dom.EventTarget]): Selection[dom.EventTarget] = {
        val tsConfigs: Map[TimeSeriesKey, TimeSeriesConfig] =
          AppState.timeSeries.now().getOrElse(cfg.id, Map.empty)

        d3Data.data.collect {
          case (ts, entries) if tsConfigs.contains(ts) => (tsConfigs(ts), entries)
        }.foreach { case (ts, entries) =>
          sel
            .append("path")
            .attr("class", "zmxLine")
            .attr("fill", "none")
            .attr("stroke", ts.color.toHex)
            .attr("stroke-width", 1.5)
            .attr(
              "d",
              d3.line()
                .curve(d3.curveCatmullRom.alpha(ts.tension))(
                  entries
                    .map(e => (xScale(e.when), yScale(e.value)))
                    .filter { case (x, y) => x >= 0 && y >= 0 }
                    .map { case (x, y) =>
                      js.Tuple2(x, y)
                    }
                    .toJSArray
                )
            )
        }
        sel
      }

      d3
        // Navigate into the SVG element
        .selectPath(s"#${chartId(cfg)}", "svg")
        // Remove the existing axes and lines
        .removeBy(".zmxLine", ".zmxAxes")
        // Each call to enhance will add something to the svg element and the return the
        // SVG element as the current selection, so that we can continue building the SVG
        .enhance(
          _.attr("viewBox", s"-${marginLeft} -${marginTop} $totalWidth $totalHeight")
            .attr("preserveAspectRatio", "none")
        )
        // Append the Axes as separate groups into the diagram
        .enhance(
          _.append("g")
            .attr("class", "zmxAxes")
            .attr("transform", s"translate(0, $chartHeight)")
            .call(d3.axisBottom(xScale))
        )
        .enhance(
          _.append("g")
            .attr("class", "zmxAxes")
            .call(d3.axisLeft(yScale))
        )
        // Now add all lines as configured for the diagram
        .enhance(addLines)

      val dur = System.currentTimeMillis() - start
      println(s"Update for diagram took $dur ms")
    }

    val metricStream: (DisplayConfig, MetricKey) => EventSource[MetricsMessage] = (cfg, m) =>
      AppState.metricMessages.now().get(m) match {
        case None    => EventStream.empty
        case Some(s) => s.events.throttle(cfg.refresh.toMillis().intValue())
      }

    def updateFromMetricsStream(cfg: DisplayConfig) =
      cfg.metrics.map(m =>
        metricStream(cfg, m) --> Observer[MetricsMessage] { msg =>
          val tsConfigs: Map[TimeSeriesKey, TimeSeriesConfig] =
            AppState.timeSeries.now().getOrElse(cfg.id, Map.empty)

          val entries = TimeSeriesEntry.fromMetricsMessage(msg)
          val noCfg   = entries.filter(e => !tsConfigs.contains(e.key))

          if (noCfg.isEmpty) {
            entries.foreach(e => recordData(cfg, e))
            update(cfg)
          } else {
            val newCfgs = noCfg.map(e => (e.key, TimeSeriesConfig(e.key, nextColor, 0.3))).toMap
            Command.observer.onNext(Command.ConfigureTimeseries(cfg.id, tsConfigs ++ newCfgs))
          }
        }
      )

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
