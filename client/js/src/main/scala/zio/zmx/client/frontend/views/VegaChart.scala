package zio.zmx.client.frontend.views

import scala.scalajs.js
import com.raquo.laminar.api.L._
import zio.zmx.client.frontend.model.PanelConfig.DisplayConfig
import zio.zmx.client.frontend.vega.Vega
import scala.util.Failure
import zio.zmx.client.frontend.model.TimeSeriesEntry
import zio.zmx.client.frontend.state.AppState
import zio.zmx.client.frontend.model.LineChartModel
import scalajs.js.JSConverters._
import zio.Chunk

object VegaChart {

  implicit val ec: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.global

  def render($cfg: Signal[DisplayConfig]): HtmlElement =
    new VegaChartImpl().render($cfg)

  private class VegaChartImpl() {

    private val vegaSchema  = "https://vega.github.io/schema/vega-lite/v5.json"
    private val vegaPadding = 5

    private val toData: TimeSeriesEntry => js.Dynamic = e =>
      js.Dynamic.literal(
        "label"     -> e.key.key,
        "timestamp" -> e.when,
        "value"     -> e.value
      )

    private def colors(cfg: DisplayConfig): js.Dynamic = {
      val tsConfigs = AppState.timeSeries
        .now()
        .getOrElse(cfg.id, Map.empty)
        .values
        .foldLeft[(Chunk[String], Chunk[String])]((Chunk.empty, Chunk.empty)) { case ((labels, colors), e) =>
          (labels :+ e.key.key, colors :+ e.color.toHex)
        }

      js.Dynamic.literal(
        "domain" -> tsConfigs._1.toJSArray,
        "range"  -> tsConfigs._2.toJSArray
      )
    }

    private def vegaDef(cfg: DisplayConfig): js.Dynamic = {
      val data = AppState.recordedData
        .now()
        .getOrElse(cfg.id, LineChartModel(cfg.maxSamples))
        .data
        .values
        .flatten
        .map(toData)
        .toJSArray

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
          "interpolate" -> "monotone",
          "tooltip"     -> true,
          "point"       -> js.Dynamic.literal(
            "filled" -> false,
            "fill"   -> "white"
          )
        ),
        "encoding" -> js.Dynamic.literal(
          "x"     -> js.Dynamic.literal(
            "field" -> "timestamp",
            "type"  -> "temporal",
            "title" -> "T"
          ),
          "y"     -> js.Dynamic.literal(
            "field" -> "value",
            "type"  -> "quantitative",
            "title" -> "V"
          ),
          "color" -> js.Dynamic.literal(
            "field" -> "label",
            "type"  -> "nominal",
            "scale" -> colors(cfg)
          )
        )
      )
    }

    private def update(el: HtmlElement, cfg: DisplayConfig): HtmlElement = {
      Vega
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

      el
    }

    def render($cfg: Signal[DisplayConfig]): HtmlElement =
      div(
        cls := "w-full h-full",
        children <-- $cfg.map { cfg =>
          Seq(
            div(
              cls := "w-full h-full",
              child <-- AppState.updatedData.events.filter(_ == cfg.id).map { uid =>
                div(
                  cls := "w-full h-full",
                  inContext { el =>
                    println(s"Updating Panel <$uid>")
                    update(el, cfg)
                  }
                )
              }
            )
          )
        }
      )
  }
}
