package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._

import com.raquo.airstream.core.Signal
import zio.zmx.client.frontend.model._
import zio.zmx.client.frontend.model.Layout._

import zio.zmx.client.frontend.components._
import zio.zmx.client.frontend.utils.Modifiers._
import zio.zmx.client.frontend.state.AppState
import zio.zmx.client.frontend.model.Layout.Dashboard.Cell
import zio.zmx.client.frontend.model.Layout.Dashboard.HGroup
import zio.zmx.client.frontend.model.Layout.Dashboard.VGroup
import zio.zmx.client.frontend.model.Layout.Dashboard.Empty

object DashboardView {

  def render($cfg: Signal[Dashboard[PanelConfig]]) = new DashboardViewImpl($cfg).render()

  private class DashboardViewImpl($cfg: Signal[Dashboard[PanelConfig]]) {

    def renderDashboardPanel(cfg: Dashboard[PanelConfig]): HtmlElement = {
      println(s"$cfg")
      cfg match {
        case Empty         => div()
        case Cell(config)  =>
          div(
            cls := "flex flex-grow",
            Panel(s"I am a cell: ${config.title}").amend(cls := "p-3 m-1 flex-grow border-accent-focus border-2")
          )
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
    }

    def render(): HtmlElement =
      div(
        displayWhen(AppState.connected),
        cls := "flex flex-grow",
        child <-- $cfg.map(renderDashboardPanel)
      )
  }
}
