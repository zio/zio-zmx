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

    private val cellStream = new EventBus[PanelConfig]

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

    private val dataSnapshot = new EventBus[Map[String, LineChartModel]]

    def render($cfg: Signal[PanelConfig]) =
      div(
        cls := "flex flex-col flex-grow",
        createPanel($cfg)
      )

    private def panelHead($cfg: Signal[PanelConfig]): HtmlElement =
      div(
        cls := "flex flex-row border-b-2 border-accent",
        children <-- $cfg.map { cfg =>
          Seq(
            span(cls := "flex-grow", s"${cfg.title}"),
            panelControls($cfg)
          )
        }
      )

    private val configId: PanelConfig => String = cfg => s"config-${cfg.id}"
    private val editId: PanelConfig => String   = cfg => s"edit-${cfg.id}"

    private def panelControls($cfg: Signal[PanelConfig]): HtmlElement = {
      val btnStyle: String => String = s => s"btn btn-$s btn-circle btn-xs m-0.5"
      div(
        cls := "flex justify-end",
        children <-- $cfg.map { cfg =>
          Seq(
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
                  div(
                    div(
                      dataTip(Signal.fromValue("Configure ...")),
                      cls := "tooltip",
                      a(
                        href := s"#${configId(cfg)}",
                        cls := btnStyle("primary"),
                        settings(svg.className := "h-1/2 w-1/2")
                      ),
                      showPanelConfig(configId(cfg), $cfg.map(_.asInstanceOf[DisplayConfig]))
                    ),
                    div(
                      dataTip(Signal.fromValue("Edit Vega Lite Spec")),
                      cls := "tooltip",
                      a(
                        href := s"#${editId(cfg)}",
                        cls := btnStyle("primary"),
                        edit(svg.className := "h-1/2 w-1/2"),
                        onClick.mapToEvent --> { _ =>
                          println("Refreshing data snapshot")
                          dataSnapshot.emit(AppState.recordedData.now())
                        }
                      ),
                      showVegaEdit(editId(cfg), $cfg.map(_.asInstanceOf[DisplayConfig]))
                    )
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
      )
    }

    private def createPanel($cfg: Signal[PanelConfig]) =
      Panel(
        panelHead($cfg),
        div(
          cls := "flex-grow relative",
          div(
            cls := "absolute w-full h-full",
            child <-- $cfg.map { cfg =>
              cfg match {
                case cfg: EmptyConfig   => emptyPanel(cfg)
                case cfg: DisplayConfig =>
                  cfg.display match {
                    case DisplayType.Diagram => diagramPanel($cfg.map(_.asInstanceOf[DisplayConfig]))
                    case DisplayType.Summary => summaryPanel($cfg.map(_.asInstanceOf[DisplayConfig]))
                  }
              }
            }
          )
        )
      ).amend(cls := "p-3 h-full border-accent-focus border-2 flex flex-col")

    private def emptyPanel(cfg: EmptyConfig): HtmlElement =
      div(
        cls := "absolute w-full h-full flex flex-column place-items-center",
        div(
          cls := "grid grid-col-1 w-full",
          span(cls := "m-auto", "Please configure me!"),
          a(
            cls := "btn btn-primary btn-circle btn-lg m-auto",
            href := s"#${configId(cfg)}",
            plus(svg.className := "h-1/2 w-1/2")
          ),
          showPanelConfig(
            configId(cfg),
            Signal.fromValue(
              DisplayConfig(
                cfg.id,
                DisplayType.Diagram,
                cfg.title,
                Chunk.empty,
                5.seconds,
                10,
                None
              )
            )
          )
        )
      )

    private def showPanelConfig(dlgId: String, $cfg: Signal[DisplayConfig]): HtmlElement =
      PanelConfigDialog.render($cfg, dlgId)

    private def showVegaEdit(dlgId: String, $cfg: Signal[DisplayConfig]): HtmlElement =
      VegaSpecDialog.render($cfg, dlgId, dataSnapshot.events.toSignal(Map.empty))

    private def diagramPanel($cfg: Signal[DisplayConfig]): HtmlElement =
      VegaChart.render($cfg)

    private def summaryPanel($cfg: Signal[DisplayConfig]): HtmlElement =
      div(
        child <-- $cfg.map { cfg =>
          span(cls := "m-auto", s"Summaries coming soon...(${cfg.metrics.size})")
        }
      )
  }

}
