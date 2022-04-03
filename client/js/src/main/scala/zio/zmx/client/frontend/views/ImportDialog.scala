package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._

import zio.json._
import zio.zmx.client.frontend.model.Layout.Dashboard
import zio.zmx.client.frontend.model.PanelConfig
import zio.zmx.client.frontend.state.{AppState, Command}

object ImportDialog {

  def render(dialogId: String): HtmlElement =
    new ImportDialogImpl(dialogId).render()

  private class ImportDialogImpl(dialogId: String) {

    private val userInputBus = new EventBus[String]

    private val parsedDashboard = Var(
      Option.empty[Dashboard[PanelConfig]],
    )

    private val userInputError = Var(
      Option.empty[String],
    )

    def render(): HtmlElement = div(
      idAttr := dialogId,
      cls    := "modal",
      div(
        cls := "modal-box max-w-full h-5/6 mx-12 border-2 flex flex-col bg-accent-focus text-accent-content overflow-y-auto",
        div(
          cls := "border-b-2",
          span("Import Dashboard"),
        ),
        div(
          cls := "label flex-none",
          span(
            cls := "label-text text-xl",
            "To load your previously saved dashboard, paste the JSON below and click the Import button.",
          ),
        ),
        div(
          cls := "flex flex-col flex-grow",
          textArea(
            cls := "w-full h-full overflow-auto",
            value <-- userInputBus.events,
            onInput.mapToValue --> userInputBus,
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
            href := "#",
            cls  := "btn btn-primary",
            cls.toggle("btn-disabled") <-- userInputBus.events.debounce(500).map { json =>
              json.trim().fromJson[Dashboard[PanelConfig]] match {

                case Right(dashboard) =>
                  parsedDashboard.set(Option(dashboard))
                  userInputError.set(None)
                  false

                case Left(exception) =>
                  userInputError.set(
                    Option(exception.toString()),
                  )
                  true
              }
            },
            onClick.map { _ =>
              val newDashboard =
                parsedDashboard
                  .now()
                  .getOrElse(
                    AppState.dashBoard.now(),
                  )
              Command.ImportDashboard(newDashboard)
            } --> Command.observer,
            "Import",
          ),
        ),
        div(
          cls := Seq("alert", "alert-error"),
          cls.toggle("visibility: hidden") <-- userInputError.signal.map(_.isEmpty),
          div(
            cls := "flex-1",
            child.maybe <-- userInputError.signal.map { v =>
              v.map(error => s"Error parsing your input: $error")
            },
          ),
        ),
      ),
    )
  }
}
