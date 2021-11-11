package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._

import zio.zmx.client.frontend.state._
import zio.zmx.client.frontend.views._

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

  def renderConnectButton: HtmlElement =
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
          cls := "p-3 w-full bg-gray-900 text-gray-50 rounded",
          span(
            cls := "text-4xl font-bold",
            "ZIO ZMX ScalaJS Client"
          )
        ),
        div(
          form(
            label("URL", cls := "px-2 font-normal text-xl content-center text-white"),
            input(
              cls := "p-2 mx-2 font-normal text-gray-600 rounded-xl",
              value <-- AppState.connectUrl.signal,
              placeholder := "Enter the WS url to connect to",
              inContext(thisNode => onInput.map(_ => thisNode.ref.value) --> newUrl)
            ),
            renderConnectButton,
            onSubmit.mapTo(Command.Connect(newUrl.now())) --> Command.observer
          ),
          child <-- shouldConnect.map(renderWebsocket)
        ),
        SummaryTables.summaries,
        diagrams
      )
    )
}
