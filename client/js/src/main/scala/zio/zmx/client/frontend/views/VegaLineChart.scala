package zio.zmx.client.frontend.views

import scala.scalajs.js
import com.raquo.laminar.api.L._
import zio.zmx.client.frontend.model.PanelConfig.DisplayConfig
import zio.zmx.client.frontend.vega.VegaEmbed
import scala.util.Failure
import scala.util.Success

object VegaLineChart {

  implicit val ec: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.global

  def render($cfg: Signal[DisplayConfig]): HtmlElement =
    new VegaLineChartImpl().render($cfg)

  private class VegaLineChartImpl() {

    private def dataEl(cat: String, value: Int) =
      js.Dynamic.literal("a" -> cat, "b" -> value)

    private def vegaDef: js.Dynamic = {
      val data = js.Array(dataEl("A", 28), dataEl("B", 56))

      js.Dynamic.literal(
        "$schema"     -> "https://vega.github.io/schema/vega-lite/v5.json",
        "description" -> "A simple bar chart with embedded data.",
        "width"       -> "container",
        "height"      -> "container",
        "padding"     -> 5,
        "data"        -> js.Dynamic.literal(
          "values" -> data
        ),
        "mark"        -> "bar",
        "encoding"    -> js.Dynamic.literal(
          "x" -> js.Dynamic.literal(
            "field" -> "a",
            "type"  -> "nominal",
            "axis"  -> js.Dynamic.literal("labelAngle" -> 0)
          ),
          "y" -> js.Dynamic.literal(
            "field" -> "b",
            "type"  -> "quantitative"
          )
        )
      )
    }

    def render($cfg: Signal[DisplayConfig]): HtmlElement =
      div(
        cls := "w-full h-full",
        inContext { el =>
          el.amend(
            onMountCallback { _ =>
              VegaEmbed
                .embed(
                  el.ref,
                  vegaDef,
                  js.Dynamic.literal("logLevel" -> "Debug")
                )
                .toFuture
                .onComplete {
                  case Failure(exception) =>
                    println(exception.getMessage())
                  case Success(o)         => println(o)
                }
            }
          )
        }
      )
  }
}
