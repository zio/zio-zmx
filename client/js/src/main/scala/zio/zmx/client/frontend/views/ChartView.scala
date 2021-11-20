package zio.zmx.client.frontend.views

import scalajs.js
import org.scalajs.dom
import org.scalajs.dom.html.Canvas
import scala.util.Random

import scalajs.js.annotation.JSImport
import com.raquo.laminar.api.L._

import java.time.Instant
import com.raquo.laminar.nodes.ReactiveHtmlElement

import scala.collection.mutable
import zio.zmx.client.frontend.model._
import zio.zmx.client.frontend.model.PanelConfig._

import zio.zmx.client.frontend.utils.DomUtils.Color
import zio.zmx.client.frontend.utils.Implicits._
import zio.zmx.client.frontend.state.AppState
import zio.zmx.client.MetricsMessage
import zio.metrics.MetricKey

/**
 * A chart represents the visible graphs within a ChartView. At this point we are
 * simply exposing the update method of chart.js, so that we can manipulate the config
 * of the graphs after they have been created-
 *
 * @param ctx - The HTML element that contains the graph, should be a canvas
 * @param config - The initial config for the Chart as described in the Chart.JS docs.
 *
 * Also see https://www.chartjs.org/docs/latest/
 */
@js.native
@JSImport("chart.js/auto", JSImport.Default)
class Chart(ctx: dom.Element, config: js.Dynamic) extends js.Object {
  // delegate to the Chart.js update function when required
  def update(mode: js.UndefOr[String]): Unit = js.native
}

object ChartView {

  def render($cfg: Signal[DisplayConfig]): HtmlElement =
    new ChartViewImpl($cfg).render()

  // A Time series is a configurable line within a chart. It is described
  // by the configuration data in the config case class and keeps itt´s
  // data in a JavaScript Array
  final private case class TimeSeries(
    cfg: TimeSeriesConfig,
    data: js.Array[js.Dynamic] = js.Array()
  ) {
    // Just record another data point, if we already have the maximum
    // number of samples, we simply shift the underlying array
    def recordData(when: Instant, value: Double): Unit = {
      if (data.size == cfg.maxSize) {
        data.shift()
      }
      val _ = data.push(
        js.Dynamic.literal(
          x = when.toEpochMilli().doubleValue(),
          y = value
        )
      )
    }

    // Prepare the config JavaScript object required to visualize the line in Chart.js
    def asDataSet: js.Dynamic = {
      val label: String = cfg.key.subKey.getOrElse(cfg.key.metric.longName)
      js.Dynamic.literal(
        label = label,
        borderColor = cfg.color.toHex,
        fill = false,
        tension = cfg.tension,
        data = data
      )
    }
  }

  private class ChartViewImpl($cfg: Signal[DisplayConfig]) {

    // Just to be able to generate new random colors when initializing new Timeseries
    private val rnd = new Random()

    // This is the map of "lines" displayed within the chart.
    private val series: mutable.Map[TimeSeriesKey, TimeSeries] = mutable.Map.empty
    private def nextColor(): Color                             = Color(rnd.nextInt(240), rnd.nextInt(240), rnd.nextInt(240))

    {
      // The date adapter is required to display the lables on the X-Axis
      val _ = ScalaDateAdapter.install()
    }

    private val options: js.Dynamic = js.Dynamic.literal(
      `type` = "line",
      options = js.Dynamic.literal(
        parsing = false,
        animation = true,
        responsive = true,
        maintainAspectRatio = true,
        scales = js.Dynamic.literal(
          x = js.Dynamic.literal(
            `type` = "time",
            time = js.Dynamic.literal(
              unit = "minute"
            ),
            grid = js.Dynamic.literal(
              drawOnChartArea = false
            )
          )
        )
      ),
      data = {
        val ds = series.view.values.map(_.asDataSet).toSeq
        js.Dynamic.literal(datasets = js.Array(ds: _*))
      }
    )

    // Add a new Timeseries, only if the graph does not contain a line for the key yet
    // The key is the string representation of a metrickey, in the case of histograms, summaries and setcounts
    // it identifies a single stream of samples within the collection of the metric
    private def addTimeseries(tsCfg: TimeSeriesConfig): Unit =
      chart.foreach { c =>
        if (!series.contains(tsCfg.key)) {
          val ts = TimeSeries(tsCfg)
          val _  = series.put(tsCfg.key, ts)
          c
            .asInstanceOf[js.Dynamic]
            .selectDynamic("data")
            .selectDynamic("datasets")
            .asInstanceOf[js.Array[js.Dynamic]]
            .push(ts.asDataSet)
        }
      }

    def recordData(entry: TimeSeriesEntry): Unit = {
      if (series.get(entry.key).isEmpty) {
        addTimeseries(TimeSeriesConfig(entry.key, nextColor(), 0.5, 100))
      }
      series.get(entry.key).foreach { ts =>
        ts.recordData(entry.when, entry.value)
      }
    }

    private def mount(canvas: ReactiveHtmlElement[Canvas]): Unit =
      chart = Some(new Chart(canvas.ref, options))

    private def update(): Unit = {
      val start = System.currentTimeMillis()
      chart.foreach(_.update(()))
      val dur   = System.currentTimeMillis() - start
      println(s"Update for diagram took $dur ms")
    }

    val metricStream: (DisplayConfig, MetricKey) => EventSource[MetricsMessage] = (cfg, m) =>
      AppState.metricMessages.now().get(m) match {
        case None    => EventStream.empty
        case Some(s) => s.events.throttle(cfg.refresh.toMillis().intValue())
      }

    def render(): HtmlElement =
      div(
        cls := "flex-grow",
        child <-- $cfg.map { cfg =>
          div(
            cls := "grid grid-col-1 place-items-stretch h-full",
            cfg.metrics.map(m =>
              metricStream(cfg, m) --> Observer[MetricsMessage] { msg =>
                println(s"Received $msg")
                TimeSeriesEntry.fromMetricsMessage(msg).foreach(recordData)
                update()
              }
            ),
            div(
              cls := "m-12",
              div(
                canvas(
                  cls := "border-2 border-red-500",
                  className := "zmxchart",
                  onMountCallback { el =>
                    mount(el.thisNode)
                  }
                )
              )
            )
          )
        }
      )

    private var chart: Option[Chart] = None

  }

}
