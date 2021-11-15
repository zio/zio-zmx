package zio.zmx.client.frontend.components

import com.raquo.laminar.api.L._

import zio.zmx.client.frontend.state._

import zio.zmx.client.frontend.icons.SVGIcon._

object NavBar {

  private val shouldConnect = AppState.shouldConnect.signal
  private val newUrl        = Var(AppState.connectUrl.now())

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

  val render: HtmlElement =
    Panel(
      cls := "navbar",
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
        div(
          cls := "dropdown dropdown-end",
          a(
            tabIndex := 0,
            cls := "btn btn-primary m-3",
            settings(svg.className := "w-5/6 h-5/6")
            // onClick.map(_ => Command.RemoveDiagram(d)) --> Command.observer
          ),
          div(
            tabIndex := 0,
            cls := "p-2 shadow menu dropdown-content bg-neutral rounded-box w-52",
            themes
          )
        ),
        child <-- shouldConnect.map(renderWebsocket)
      )
    )

}
