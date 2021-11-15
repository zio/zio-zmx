package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._

import zio.zmx.client.frontend.state._
import zio.zmx.client.frontend.views._

import zio.zmx.client.frontend.utils.Modifiers._
import zio.zmx.client.frontend.components._

object MainView {

  private val sigDashboard = AppState.dashBoard.signal
  private val themeSignal  = AppState.theme.signal

  def render: Div =
    div(
      dataTheme(themeSignal),
      cls := "px-3 pb-3 w-screen h-screen flex flex-col bg-accent",
      NavBar.render,
      DashboardView.render(sigDashboard)
    )
}
