package zio.zmx.client.frontend.views

import scalajs.js
import org.scalajs.dom
import org.scalajs.dom.html.Canvas
import org.scalajs.dom.ext.Color
import scala.util.Random

import scalajs.js.annotation.JSImport
import com.raquo.laminar.api.L._

import java.time.Instant
import com.raquo.laminar.nodes.ReactiveHtmlElement

import scala.collection.mutable
import zio.zmx.client.frontend.model._
import zio.zmx.client.frontend.utils.Implicits._

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
  def update(mode: js.UndefOr[String]): Unit = js.native
}

/**
 * A chart view is the combined view of a canvas displaying the actual graphs and additional elements
 * with textual information about the view and/or HTML elements to manipulate the configuration of the
 * view.
 */
object ChartView {

  final private case class TimeSeries(
    cfg: TimeSeriesConfig,
    data: js.Array[js.Dynamic] = js.Array()
  ) {
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

  final case class ChartView() {

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
        maintainAspectRatio = false,
        scales = js.Dynamic.literal(
          x = js.Dynamic.literal(
            `type` = "timeseries"
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
          update()
        }
      }

    def recordData(entry: TimeSeriesEntry): Unit =
      series.get(entry.key) match {
        case None     => addTimeseries(TimeSeriesConfig(entry.key, nextColor(), 0.5, 100))
        case Some(ts) => ts.recordData(entry.when, entry.value)
      }

    private def mount(canvas: ReactiveHtmlElement[Canvas]): Unit =
      chart = Some(new Chart(canvas.ref, options))

    def update(): Unit =
      chart.foreach(_.update(()))

    def element(): HtmlElement =
      // The actual canvas takes the left half of the container
      div(
        cls := "w-4/5 bg-gray-900 text-gray-50 rounded my-3 p-3 h-80 flex",
        div(
          cls := "w-full h-full rounded bg-gray-50 p-3",
          div(
            div(
              cls := "h-full",
              position.relative,
              canvas(
                width("100%"),
                height("100%"),
                onMountCallback { el =>
                  mount(el.thisNode)
                }
              )
            )
          )
        )
      )

    private var chart: Option[Chart] = None
  }

}
