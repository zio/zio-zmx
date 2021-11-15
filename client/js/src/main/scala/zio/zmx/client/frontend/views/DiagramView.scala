package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._

import zio.zmx.client.frontend.icons.SVGIcon._
import zio.zmx.client.frontend.components._
import zio.zmx.client.frontend.model.PanelConfig._

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

    private def diagramControls(d: DiagramConfig): HtmlElement =
      div(
        cls := "w-1/5 flex justify-end",
        a(
          cls := "btn btn-primary btn-circle m-3",
          arrowUp(svg.className := "h-1/2 w-1/2")
          //onClick.map(_ => Command.MoveDiagram(d, Direction.Up)) --> Command.observer
        ),
        a(
          cls := "btn btn-primary btn-circle m-3",
          arrowDown(svg.className := "h-1/2 w-1/2")
          //onClick.map(_ => Command.MoveDiagram(d, Direction.Down)) --> Command.observer
        ),
        a(
          cls := "btn btn-primary btn-circle m-3",
          settings(svg.className := "h-1/2 w-1/2")
          // onClick.map(_ => Command.RemoveDiagram(d)) --> Command.observer
        ),
        a(
          cls := "btn btn-secondary btn-circle m-3",
          close(svg.className := "h-1/2 w-1/2")
          //onClick.map(_ => Command.RemoveDiagram(d)) --> Command.observer
        )
      )

    private def chartConfig(d: DiagramConfig): HtmlElement =
      div(
        cls := "w-full h-full",
        form(
          cls := "flex flex-col",
          // onSubmit.preventDefault.mapTo(
          //   Command.UpdateDiagram(d.copy(title = titleVar.now()))
          // ) --> Command.observer,
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
              cls := "flex-grow btn btn-primary",
              typ("submit"),
              "Apply"
            )
          )
        )
      )

    def render(): HtmlElement =
      div(
        child <-- $cfg.map { cfg =>
          Panel(
            cls := "card",
            div(
              cls := "card-title flex flex-row",
              h1(
                cls := "w-4/5 m-2",
                cfg.title
              ),
              diagramControls(cfg)
            ),
            div(
              cls := "card-body h-96 flex flex-row",
              div(
                cls := "w-4/5 h-full",
                ChartView.render($cfg)
              ),
              div(
                cls := "card-actions ml-3 w-1/5 h-full",
                chartConfig(cfg)
              )
            )
          )

        }
      )
  }
}
