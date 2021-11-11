package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._

import zio.zmx.client.frontend.state._
import zio.zmx.client.frontend.views._
import zio.zmx.client.frontend.icons.SVGIcon._

import zio.zmx.client.frontend.utils.Modifiers._
import zio.zmx.client.frontend.utils.Implicits._

object MainView {

  private val shouldConnect = AppState.shouldConnect.signal
  private val newUrl        = Var(AppState.connectUrl.now())
  private val sigDiagrams   = AppState.diagrams.signal

  private val diagrams: HtmlElement =
    div(
      displayWhen(sigDiagrams.map(_.nonEmpty)),
      children <-- sigDiagrams.split(_.id)(DiagramView.render)
    )

  private val renderUrlForm: HtmlElement =
    div(
      cls := "flex flex-row items-center",
      h1("URL", cls := "mx-3"),
      input(
        cls := "input text-lg p-2 mx-2",
        value <-- AppState.connectUrl.signal,
        placeholder := "Enter a WS URL",
        inContext(thisNode => onInput.map(_ => thisNode.ref.value) --> newUrl)
      ),
      a(
        href("#"),
        child <-- shouldConnect.map(b => if (b) "Disconnect" else "Connect"),
        className := "btn",
        // The color settings are coming from a signal -- they must not clash with any of the static settings
        className <-- shouldConnect.map(b => if (b) "btn-secondary" else "btn-primary"),
        onClick.map(_ =>
          if (shouldConnect.now()) Command.Disconnect else Command.Connect(newUrl.now())
        ) --> Command.observer
      )
    )

  def renderWebsocket(connect: Boolean): HtmlElement = if (connect)
    div(child <-- AppState.connectUrl.signal.map(WebsocketHandler.render))
  else
    div()

  def render: Div =
    div(
      inContext { thisNode =>
        thisNode.ref.setAttribute("data-theme", "dark")
        thisNode
      },
      cls := "flex flex-column",
      div(
        cls := "p-6 w-full",
        div(
          cls := "navbar bg-neutral text-neutral-content rounded-box text-lg font-bold",
          img(
            src := "/ZIO.png"
          ),
          h1(
            cls := "navbar-start mx-3",
            "ZIO ZMX Developer´s Client"
          ),
          div(
            cls := "navbar-end",
            form(
              cls := "my-auto",
              renderUrlForm,
              onSubmit.mapTo(Command.Connect(newUrl.now())) --> Command.observer
            ),
            a(
              cls := "btn btn-primary m-3",
              settings(svg.className := "w-5/6 h-5/6")
              // onClick.map(_ => Command.RemoveDiagram(d)) --> Command.observer
            ),
            child <-- shouldConnect.map(renderWebsocket)
          )
        ),
        SummaryTables.summaries,
        diagrams
      )
    )
}
