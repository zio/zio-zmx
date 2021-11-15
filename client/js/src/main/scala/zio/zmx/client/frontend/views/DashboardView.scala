package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._

import com.raquo.airstream.core.Signal
import zio.zmx.client.frontend.model._

import zio.zmx.client.frontend.components._
import zio.zmx.client.frontend.utils.Modifiers._
import zio.zmx.client.frontend.state.AppState

object DashboardView {

  def render($cfg: Signal[DashboardConfig[PanelConfig]]) = new DashboardViewImpl($cfg).render()

  private class DashboardViewImpl($cfg: Signal[DashboardConfig[PanelConfig]]) {

    def render(): HtmlElement =
      Panel(
        displayWhen(AppState.connected),
        cls := "w-full flex-grow",
        h1(
          child <-- $cfg.map(_.view.title)
        )
      )
  }
}
