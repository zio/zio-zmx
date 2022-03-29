package zio.zmx.client.frontend.views

import scala.scalajs.js
import scala.util.Failure

import com.raquo.laminar.api.L._

import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import zio.zmx.client.frontend.model._
import zio.zmx.client.frontend.model.PanelConfig.DisplayConfig
import zio.zmx.client.frontend.state.AppState
import zio.zmx.client.frontend.vega.Vega

object VegaChart {

  def render($cfg: Signal[DisplayConfig]): HtmlElement =
    new VegaChartImpl().render($cfg)

  final private class VegaChartImpl() {

    private def update(el: HtmlElement, cfg: DisplayConfig): HtmlElement = {
      Vega
        .embed(
          el.ref,
          VegaModel(cfg, AppState.recordedData.now().getOrElse(cfg.id, LineChartModel(cfg.maxSamples))).vegaDef,
          js.Dynamic.literal("logLevel" -> "Debug", "actions" -> false),
        )
        .toFuture
        .onComplete {
          case Failure(exception) =>
            println(exception.getMessage)
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
              child <-- AppState.updatedData.events.filter(_ == cfg.id).map { _ =>
                div(
                  cls := "w-full h-full",
                  inContext { el =>
                    update(el, cfg)
                  },
                )
              },
            ),
          )
        },
      )
  }
}
