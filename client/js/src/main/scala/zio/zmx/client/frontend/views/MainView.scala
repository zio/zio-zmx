package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._

import zio.zmx.client.frontend.state._
import zio.zmx.client.frontend.views._

import zio.zmx.client.frontend.utils.Modifiers._
import zio.zmx.client.frontend.utils.Implicits._
import zio.zmx.client.frontend.components.NavBar

object MainView {

  private val sigDiagrams = AppState.diagrams.signal
  private val themeSignal = AppState.theme.signal

  private val diagrams: HtmlElement =
    div(
      displayWhen(sigDiagrams.map(_.nonEmpty)),
      children <-- sigDiagrams.split(_.id)(DiagramView.render)
    )

  def render: Div =
    div(
      dataTheme(themeSignal),
      cls := "flex flex-col",
      NavBar.render,
      SummaryTables.summaries,
      diagrams
    )
}
