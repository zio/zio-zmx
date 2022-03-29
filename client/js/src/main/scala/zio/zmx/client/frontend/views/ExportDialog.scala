package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._

import zio.json._
import zio.zmx.client.frontend.model.Layout.Dashboard
import zio.zmx.client.frontend.model.PanelConfig
import zio.zmx.client.frontend.state.AppState

object ExportDialog {

  implicit private lazy val dashboardEncoder = Dashboard.jsonEncoder[PanelConfig]

  def render(dialogId: String): HtmlElement =
    new ExportDialogImpl(dialogId).render()

  private class ExportDialogImpl(dialogId: String) {

    def render(): HtmlElement = div(
      idAttr := dialogId,
      cls    := "modal",
      child <-- AppState.dashBoard.signal.map { dashboard =>
        val json = dashboard.toJson

        div(
          cls := "modal-box max-w-full h-5/6 mx-12 border-2 flex flex-col bg-accent-focus text-accent-content overflow-y-auto",
          div(
            cls := "border-b-2",
            span("Export Dashboard"),
          ),
          div(
            cls := "label flex-none",
            span(
              cls := "label-text text-xl",
              "To export your current dashboard in its entirety, select and copy the following JSON and save it in a file.",
            ),
          ),
          div(
            cls := "flex flex-col flex-grow",
            textArea(
              cls      := "w-full h-full overflow-auto",
              readOnly := true,
              json,
            ),
          ),
          div(
            cls := "modal-action",
            a(
              href := "#",
              cls  := "btn btn-primary",
              "Close",
            ),
          ),
        )
      },
    )
  }
}
