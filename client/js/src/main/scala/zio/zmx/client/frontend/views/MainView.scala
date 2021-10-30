package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._

import zio.zmx.client.frontend.state._
import zio.zmx.client.frontend.views._

import zio.zmx.client.frontend.utils.Implicits._

object MainView {
  private val diagrams: HtmlElement =
    div(
      div(
        cls := "bg-gray-900 text-gray-50 rounded p-3 my-3",
        span(
          cls := "text-3xl font-bold my-2",
          "Diagrams"
        )
      ),
      children <-- AppState.diagramConfigs.signal.split(cfg => cfg.id)(DiagramView.render)
    )

  def view: Div =
    div(
      cls := "flex",
      div(
        cls := "p-6 w-full",
        div(
          cls := "p-3 w-full bg-gray-900 text-gray-50 rounded",
          span(
            cls := "text-4xl font-bold",
            "ZIO ZMX ScalaJS Client"
          )
        ),
        SummaryTables.summaries,
        diagrams
      )
    )
}
