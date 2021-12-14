package zio.zmx.client.frontend.views

import scala.scalajs.js
import com.raquo.laminar.api.L._
import zio.zmx.client.frontend.model.PanelConfig.DisplayConfig
import zio.zmx.client.frontend.vega.Vega
import scala.util.Failure
import zio.zmx.client.frontend.state.AppState
import zio.zmx.client.frontend.model._

object VegaChart {

  implicit val ec: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.global

  def render($cfg: Signal[DisplayConfig]): HtmlElement =
    new VegaChartImpl().render($cfg)

  private class VegaChartImpl() {

    private def update(el: HtmlElement, cfg: DisplayConfig): HtmlElement = {
      Vega
        .embed(
          el.ref,
          VegaModel(cfg).vegaDef,
          js.Dynamic.literal("logLevel" -> "Debug", "actions" -> false)
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
