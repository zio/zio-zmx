package zio.zmx.client.frontend.views

import scala.util.Try

import com.raquo.laminar.api.L._

import zio.zmx.client.frontend.model.LineChartModel
import zio.zmx.client.frontend.model.PanelConfig.DisplayConfig
import zio.zmx.client.frontend.model.VegaModel
import zio.zmx.client.frontend.state.Command
import zio.zmx.client.frontend.vega.VegaEditorProxy

import scalajs.js

object VegaSpecDialog {

  def render(
    $cfg: Signal[DisplayConfig],
    dlgId: String,
    dataSnapshot: Signal[Map[String, LineChartModel]],
  ): HtmlElement =
    new VegaSpecDialogImpl($cfg, dlgId, dataSnapshot).render()

  private class VegaSpecDialogImpl(
    $cfg: Signal[DisplayConfig],
    dlgId: String,
    dataSnapshot: Signal[Map[String, LineChartModel]]) {

    private val curSpec: Var[String] = Var("")

    private val vegaSignal = $cfg.combineWithFn(dataSnapshot) { case (cfg, models) =>
      (cfg, models.getOrElse(cfg.id, LineChartModel(cfg.maxSamples)))
    }

    def render(): HtmlElement = div(
      idAttr := dlgId,
      cls    := "modal",
      child <-- vegaSignal.map { case (cfg, model) =>
        div(
          cls := "modal-box max-w-full h-5/6 mx-12 border-2 flex flex-col bg-accent-focus text-accent-content overflow-y-auto",
          div(
            cls := "border-b-2",
            span("Edit Vega configuration"),
          ),
          div(
            cls := "flex flex-col flex-grow",
            textArea(
              cls := "w-full h-full overflow-auto",
              onMountCallback(_ => curSpec.set(VegaModel(cfg, model).vegaDefJson)),
              onInput.mapToValue --> curSpec,
              VegaModel(cfg, model).vegaDefJson,
            ),
          ),
          div(
            cls := "modal-action",
            a(
              href := "#",
              cls  := "btn btn-secondary",
              "Cancel",
            ),
            a(
              href := s"#$dlgId",
              cls  := "btn btn-primary",
              onClick.mapToEvent --> { _ =>
                new VegaEditorProxy(cfg, curSpec.now()).open()
              },
              "Edit in Vega",
            ),
            a(
              href := "#",
              cls  := "btn btn-primary",
              onClick.mapToEvent --> { _ =>
                val newSpec = Try(js.JSON.parse(curSpec.now())).toOption
                newSpec.foreach { s =>
                  println("Updating Vega Spec in config")
                  val newCfg = cfg.copy(vegaConfig = Some(s))
                  Command.observer.onNext(Command.UpdateDashboard(newCfg))
                }
              },
              "Apply",
            ),
          ),
        )
      },
    )
  }
}
