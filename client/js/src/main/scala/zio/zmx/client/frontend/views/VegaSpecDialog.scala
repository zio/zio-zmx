package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._

import zio.zmx.client.frontend.model.PanelConfig.DisplayConfig
import zio.zmx.client.frontend.model.VegaModel
import zio.zmx.client.frontend.state.AppState
import zio.zmx.client.frontend.model.LineChartModel

object VegaSpecDialog {

  def render($cfg: Signal[DisplayConfig], dlgId: String): HtmlElement =
    new VegaSpecDialogImpl($cfg, dlgId).render()

  private class VegaSpecDialogImpl($cfg: Signal[DisplayConfig], dlgId: String) {

    private val vegaSignal = $cfg.combineWithFn(AppState.recordedData.toObservable) { case (cfg, data) =>
      (cfg, data.get(cfg.id).getOrElse(LineChartModel(cfg.maxSamples)))
    }

    private val curSpec: Var[String] = Var("")

    def render(): HtmlElement = div(
      idAttr := dlgId,
      cls := "modal",
      child <-- vegaSignal.map { case (cfg, model) =>
        div(
          cls := "modal-box max-w-full h-5/6 mx-12 border-2 flex flex-col bg-accent-focus text-accent-content overflow-y-auto",
          div(
            cls := "border-b-2",
            span("Edit Vega configuration")
          ),
          div(
            cls := "flex flex-col flex-grow",
            textArea(
              cls := "w-full h-full overflow-auto",
              value <-- curSpec,
              onMountCallback(_ => curSpec.set(VegaModel(cfg, model).vegaDefJson)),
              onInput.mapToValue --> curSpec
            )
          ),
          div(
            cls := "modal-action",
            a(
              href := "#",
              cls := "btn btn-secondary",
              "Cancel"
            ),
            a(
              href := "#",
              cls := "btn btn-primary",
              "Edit in Vega"
            ),
            a(
              href := "#",
              cls := "btn btn-primary",
              "Apply"
            )
          )
        )
      }
    )
  }
}
