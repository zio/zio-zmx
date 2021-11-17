package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._

import com.raquo.airstream.core.Signal
import zio.zmx.client.frontend.model._
import zio.zmx.client.frontend.model.Layout._
import zio.zmx.client.frontend.model.Layout.Dashboard._
import zio.zmx.client.frontend.model.PanelConfig._
import zio.zmx.client.frontend.icons.SVGIcon._

import zio.zmx.client.frontend.components._
import zio.zmx.client.frontend.utils.Modifiers._
import zio.zmx.client.frontend.state.{ AppState, Command }

object DashboardView {

  def render($cfg: Signal[Dashboard[PanelConfig]]) = new DashboardViewImpl($cfg).render()

  private class DashboardViewImpl($cfg: Signal[Dashboard[PanelConfig]]) {

    private val cellStream: EventBus[PanelConfig] = new EventBus[PanelConfig]

    def renderDashboardPanel(cfg: Dashboard[PanelConfig]): HtmlElement =
      cfg match {
        case Empty         => div()
        case Cell(config)  =>
          cellStream.emit(config)
          DashboardPanel.render(cellStream.events.filter(_.id == config.id).toSignal(config))
        case HGroup(elems) =>
          div(
            cls := "flex flex-row flex-grow",
            elems.map(renderDashboardPanel)
          )
        case VGroup(elems) =>
          div(
            cls := "flex flex-col flex-grow",
            elems.map(renderDashboardPanel)
          )
      }

    def render(): HtmlElement =
      div(
        displayWhen(AppState.connected),
        cls := "flex flex-grow",
        child <-- $cfg.map(renderDashboardPanel)
      )
  }

  object DashboardPanel {

    def render($cfg: Signal[PanelConfig]) =
      div(
        cls := "flex flex-grow",
        child <-- $cfg.map(createPanel)
      )

    private def panelHead(cfg: PanelConfig): HtmlElement =
      div(
        cls := "flex flex-row border-b-2 border-accent",
        span(cls := "flex-grow", s"I am an empty cell: ${cfg.title}"),
        panelControls(cfg)
      )

    private def panelControls(cfg: PanelConfig): HtmlElement = {
      val btnStyle: String => String = s => s"btn btn-$s btn-circle btn-xs m-0.5"
      div(
        cls := "flex justify-end",
        a(
          cls := btnStyle("primary"),
          onClick.map(_ => Command.SplitHorizontal(cfg)) --> Command.observer,
          "H"
        ),
        a(
          cls := btnStyle("primary"),
          onClick.map(_ => Command.SplitVertical(cfg)) --> Command.observer,
          "V"
        ),
        a(
          cls := btnStyle("primary"),
          settings(svg.className := "h-1/2 w-1/2")
          // onClick.map(_ => Command.RemoveDiagram(d)) --> Command.observer
        ),
        a(
          cls := btnStyle("secondary"),
          close(svg.className := "h-1/2 w-1/2"),
          onClick.map(_ => Command.ClosePanel(cfg)) --> Command.observer
        )
      )
    }

    private def createPanel(cfg: PanelConfig) =
      Panel(
        panelHead(cfg),
        div(
          cls := "flex flex-row flex-grow",
          cfg match {
            case cfg: EmptyConfig   => emptyPanel(cfg)
            case cfg: DiagramConfig => diagramPanel(cfg)
            case cfg: SummaryConfig => summaryPanel(cfg)
          }
        )
      ).amend(cls := "p-3 m-1 flex flex-col flex-grow border-accent-focus border-2")

    private def emptyPanel(cfg: EmptyConfig): HtmlElement =
      div(
        cls := "flex flex-row flex-grow",
        span(cls := "m-auto", "Please configure me!")
      )

    private def diagramPanel(cfg: DiagramConfig): HtmlElement =
      div(
        cls := "flex flex-row flex-grow",
        span(cls := "m-auto", "Diagrams coming soon...")
      )

    private def summaryPanel(cfg: SummaryConfig): HtmlElement =
      div(
        cls := "flex flex-row flex-grow",
        span(cls := "m-auto", "Summaries coming soon...")
      )
  }

}
