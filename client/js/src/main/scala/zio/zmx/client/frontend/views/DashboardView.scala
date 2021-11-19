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

import zio._

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
            cls := s"grid grid-cols-${elems.size}",
            elems.map(renderDashboardPanel)
          )
        case VGroup(elems) =>
          div(
            cls := s"grid grid-rows-${elems.size}",
            elems.map(renderDashboardPanel)
          )
      }

    def render(): HtmlElement =
      div(
        displayWhen(AppState.connected),
        cls := "flex flex-grow",
        div(
          cls := "flex-grow grid grid-col-1 place-items-stretch",
          child <-- $cfg.map(renderDashboardPanel)
        )
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
        span(cls := "flex-grow", s"${cfg.title}"),
        panelControls(cfg)
      )

    private def panelControls(cfg: PanelConfig): HtmlElement = {
      val btnStyle: String => String = s => s"btn btn-$s btn-circle btn-xs m-0.5"
      div(
        cls := "flex justify-end",
        div(
          dataTip(Signal.fromValue("Split Horizontally")),
          cls := "tooltip",
          button(
            cls := btnStyle("primary"),
            onClick.map(_ => Command.SplitHorizontal(cfg)) --> Command.observer,
            dots_vertical(svg.className := "h-1/2 w-1/2")
          )
        ),
        div(
          dataTip(Signal.fromValue("Split Vertically")),
          cls := "tooltip",
          button(
            cls := btnStyle("primary"),
            onClick.map(_ => Command.SplitVertical(cfg)) --> Command.observer,
            dots_horizontal(svg.className := "h-1/2 w-1/2")
          )
        ),
        (
          cfg match {
            case _: EmptyConfig     => emptyNode
            case cfg: DisplayConfig =>
              val dlgId = s"config-${cfg.id}"
              div(
                dataTip(Signal.fromValue("Configure ...")),
                cls := "tooltip",
                a(
                  href := s"#$dlgId",
                  cls := btnStyle("primary"),
                  settings(svg.className := "h-1/2 w-1/2")
                ),
                showPanelConfig(dlgId, cfg)
              )

          }
        ),
        div(
          dataTip(Signal.fromValue("Close Panel")),
          cls := "tooltip",
          a(
            cls := btnStyle("secondary"),
            close(svg.className := "h-1/2 w-1/2"),
            onClick.map(_ => Command.ClosePanel(cfg)) --> Command.observer
          )
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
            case cfg: DisplayConfig =>
              cfg.display match {
                case DisplayType.Diagram => diagramPanel(cfg)
                case DisplayType.Summary => summaryPanel(cfg)
              }
          }
        )
      ).amend(cls := "p-3 flex flex-col flex-grow border-accent-focus border-2")

    private def emptyPanel(cfg: EmptyConfig): HtmlElement = {
      val dlgId: String = s"initPanel-${cfg.id}"

      div(
        cls := "flex flex-row flex-grow",
        div(
          cls := "grid grid-rows-2 m-auto place-items-center",
          span(cls := "m-auto", "Please configure me!"),
          a(
            cls := "btn btn-primary btn-circle btn-lg",
            href := s"#$dlgId",
            plus(svg.className := "h-1/2 w-1/2")
          ),
          showPanelConfig(
            dlgId,
            DisplayConfig(
              cfg.id,
              DisplayType.Diagram,
              cfg.title,
              Chunk.empty,
              5.seconds
            )
          )
        )
      )
    }

    private def showPanelConfig(dlgId: String, cfg: DisplayConfig): HtmlElement =
      PanelConfigDialog.render(
        DisplayConfig(
          cfg.id,
          DisplayType.Diagram,
          cfg.title,
          Chunk.empty,
          5.seconds
        ),
        s"$dlgId"
      )

    private def diagramPanel(cfg: DisplayConfig): HtmlElement =
      div(
        cls := "flex flex-row flex-grow",
        span(cls := "m-auto", "Diagrams coming soon...")
      )

    private def summaryPanel(cfg: DisplayConfig): HtmlElement =
      div(
        cls := "flex flex-row flex-grow",
        span(cls := "m-auto", "Summaries coming soon...")
      )
  }

}
