package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._
import zio.zmx.client.frontend.state.Command
import zio.zmx.client.frontend.model.PanelConfig.DisplayConfig

object PanelConfigDialog {

  def render(cfg: DisplayConfig, id: String): HtmlElement =
    new PanelConfigDialogImpl(cfg, id).render()

  private class PanelConfigDialogImpl(cfg: DisplayConfig, dlgId: String) {

    private val curTitle: Var[String] = Var(cfg.title)

    def render(): HtmlElement =
      div(
        idAttr := dlgId,
        cls := "modal",
        div(
          cls := "modal-box max-w-full m-12 border-2 flex flex-col bg-accent-focus text-accent-content",
          div(
            span(cfg.id)
          ),
          div(
            cls := "flex-grow",
            div(
              cls := "form-control",
              label(cls := "label", span(cls := "label-text", "Title")),
              input(
                tpe := "text",
                cls := "input input-primary input-bordered",
                placeholder("Enter Diagram title"),
                controlled(
                  value <-- curTitle,
                  onInput.mapToValue --> curTitle
                )
              )
            )
          ),
          div(
            cls := "modal-action",
            a(
              href := "#",
              cls := "btn btn-secondary",
              onClick.map(_ => cfg.title) --> curTitle,
              "Cancel"
            ),
            a(
              href := "#",
              cls := "btn btn-primary",
              onClick.map { _ =>
                val newCfg = cfg.copy(title = curTitle.now())
                Command.UpdateDashboard(newCfg)
              } --> Command.observer,
              "Apply"
            )
          )
        )
      )
  }
}
