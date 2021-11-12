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
  private val themeSignal   = AppState.theme.signal

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

  private val themes: HtmlElement = {

    val themeLink: Theme.DaisyTheme => HtmlElement = t =>
      a(
        cls := "text-neutral-content",
        onClick.map(_ => Command.SetTheme(t)) --> Command.observer,
        t.name
      )

    li(
      Theme.allThemes.map(themeLink)
    )
  }

  def renderWebsocket(connect: Boolean): HtmlElement = if (connect)
    div(child <-- AppState.connectUrl.signal.map(WebsocketHandler.render))
  else
    div()

  def render: Div =
    div(
      dataTheme(themeSignal),
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
            cls := "navbar-end flex",
            form(
              cls := "my-auto",
              renderUrlForm,
              onSubmit.mapTo(Command.Connect(newUrl.now())) --> Command.observer
            ),
            div(
              cls := "dropdown px-3",
              div(
                tabIndex := 0,
                cls := "m-1 btn",
                // a(
                //   cls := "btn btn-primary m-3 p-3",
                //   settings(svg.className := "")
                //   // onClick.map(_ => Command.RemoveDiagram(d)) --> Command.observer
                // )
                "Dropdown"
              ),
              ul(
                tabIndex := 0,
                cls := "p-2 shadow menu dropdown-content bg-base-100 rounded-box w-52",
                li(
                  a(
                    "hello"
                  )
                ),
                li(
                  a(
                    "world"
                  )
                )
              )
              // div(
              //   tabIndex := 0,
              //   cls := "p-2 shadow menu dropdown-content bg-neutral rounded-box w-52",
              //   themes
              // )
            ),
            child <-- shouldConnect.map(renderWebsocket)
          )
        ),
        SummaryTables.summaries,
        diagrams
      )
    )
}
