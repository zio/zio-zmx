package zio.zmx.client.frontend.views

import scala.scalajs.js
import com.raquo.laminar.api.L._
import zio.zmx.client.frontend.model.PanelConfig.DisplayConfig
import zio.zmx.client.frontend.vega.VegaEmbed
import scala.util.Failure
import zio.zmx.client.frontend.model.TimeSeriesEntry
import zio.zmx.client.frontend.utils.Implicits._
import zio.zmx.client.frontend.state.AppState
import zio.zmx.client.frontend.model.LineChartModel
import scalajs.js.JSConverters._

object VegaLineChart {

  implicit val ec: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.global

  def render($cfg: Signal[DisplayConfig]): HtmlElement =
    new VegaLineChartImpl().render($cfg)

  private class VegaLineChartImpl() {

    private val vegaSchema  = "https://vega.github.io/schema/vega-lite/v5.json"
    private val vegaPadding = 5

    private val toData: TimeSeriesEntry => js.Dynamic = e =>
      js.Dynamic.literal(
        "label"     -> e.key.metric.longName,
        "timestamp" -> e.when,
        "value"     -> e.value
      )

    private def vegaDef(cfg: DisplayConfig): js.Dynamic = {
      val data = {
        val model = AppState.recordedData.now().getOrElse(cfg.id, LineChartModel(cfg.maxSamples)).data.values.flatten
        model.map(toData).toJSArray
      }

      js.Dynamic.literal(
        "$schema"  -> vegaSchema,
        "width"    -> "container",
        "height"   -> "container",
        "padding"  -> vegaPadding,
        "data"     -> js.Dynamic.literal(
          "values" -> data
        ),
        "mark"     -> js.Dynamic.literal(
          "type"        -> "line",
          "interpolate" -> "monotone"
        ),
        "encoding" -> js.Dynamic.literal(
          "x"     -> js.Dynamic.literal(
            "field" -> "timestamp",
            "type"  -> "temporal"
          ),
          "y"     -> js.Dynamic.literal(
            "field" -> "value",
            "type"  -> "quantitative"
          ),
          "color" -> js.Dynamic.literal(
            "field" -> "label",
            "type"  -> "nominal"
          )
        )
      )
    }

    def update(el: HtmlElement, cfg: DisplayConfig): Unit =
      VegaEmbed
        .embed(
          el.ref,
          vegaDef(cfg),
          js.Dynamic.literal("logLevel" -> "Debug")
        )
        .toFuture
        .onComplete {
          case Failure(exception) =>
            println(exception.getMessage())
          case _                  => // do nothing
        }

    def render($cfg: Signal[DisplayConfig]): HtmlElement =
      div(
        cls := "w-full h-full",
        children <-- $cfg.map { cfg =>
          Seq(
            div(
              cls := "w-full h-full",
              inContext { el =>
                val tracker = new DataTracker(cfg, update)
                tracker.updateFromMetricsStream(el)
              }
            )
          )
        }
      )
  }
}
