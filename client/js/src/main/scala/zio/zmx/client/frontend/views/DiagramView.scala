package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._

import zio.zmx.client.frontend.icons.HeroIcon.SolidIcon._
import zio.zmx.client.frontend.utils.Modifiers._

import zio.zmx.client.frontend.model._
import zio.zmx.client.frontend.state._

/**
 * A DiagramView is implemented as a Laminar element and is responsible for initializing and updating
 * the graph(s) embedded within. It taps into the overall stream of change events, filters out the events
 * that are relevant for the diagram and updates the TimeSeries of the underlying Charts.
 *
 * As we might see a lot of change events, we will throttle the update interval for the graphs as specified in
 * the individual diagram config.
 */
object DiagramView {

  def render(id: String, initial: DiagramConfig, $config: Signal[DiagramConfig]): HtmlElement =
    new DiagramViewImpl($config).render()

  private class DiagramViewImpl(
    $cfg: Signal[DiagramConfig]
  ) {

    // A Chart element that will be uninitialized and can be inserted into the dom by calling
    // the element() method

    private val titleVar: Var[String] = Var("")

    def diagramControls(d: DiagramConfig): HtmlElement =
      div(
        cls := "w-1/5 flex justify-end",
        a(
          cls := "rounded text-center place-self-center h-10 w-10 text-white",
          displayWhen($cfg.map(_.displayIndex > 0)),
          arrowUp(svg.className := "h-full w-full"),
          onClick.map(_ => Command.MoveDiagram(d, Direction.Up)) --> Command.observer
        ),
        a(
          cls := "rounded text-center place-self-center h-10 w-10 text-white",
          displayWhen(AppState.diagrams.signal.map(_.size > d.displayIndex + 1)),
          arrowDown(svg.className := "h-full w-full"),
          onClick.map(_ => Command.MoveDiagram(d, Direction.Down)) --> Command.observer
        ),
        a(
          cls := "rounded text-center place-self-center h-10 w-10 text-red-500",
          close(svg.className := "h-full w-full"),
          onClick.map(_ => Command.RemoveDiagram(d)) --> Command.observer
        )
      )

    def chartConfig(d: DiagramConfig): HtmlElement =
      div(
        cls := "w-full h-full",
        form(
          cls := "flex flex-col",
          onSubmit.preventDefault.mapTo(
            Command.UpdateDiagram(d.copy(title = titleVar.now()))
          ) --> Command.observer,
          div(
            cls := "w-full flex flex-row",
            label(cls := "w-1/3 text-gray-50 text-xl font-bold", "Title: "),
            input(
              cls := "flex-grow rounded-xl px-3 text-gray-600",
              placeholder("Enter Diagram title"),
              onMountCallback(_ => titleVar.update(_ => d.title)),
              controlled(
                value <-- titleVar,
                onInput.mapToValue --> titleVar
              )
            )
          ),
          div(
            cls := "w-full my-4 flex flex-row",
            button(
              cls := "flex-grow bg-blue-500 hover:bg-blue-700 text-2xl text-white font-bold py-2 px-10 rounded text-center",
              typ("submit"),
              "Apply"
            )
          )
        )
      )

    def render(): HtmlElement =
      div(
        child <-- $cfg.map { cfg =>
          div(
            cls := "bg-gray-900 text-gray-50 rounded my-3 p-3",
            div(
              cls := "w-full flex",
              span(
                cls := "w-4/5 text-2xl font-bold my-2",
                cfg.title
              ),
              diagramControls(cfg)
            ),
            div(
              cls := "flex h-96",
              div(
                cls := "w-4/5 h-full",
                ChartView.render($cfg)
              ),
              div(
                cls := "ml-3 w-1/5 h-full",
                chartConfig(cfg)
              )
            )
          )
        }
      )
  }
}
