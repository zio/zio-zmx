package zio.zmx.client.frontend.views

import scalajs.js
import org.scalajs.dom
import scalajs.js.annotation.JSImport
import com.raquo.laminar.api.L._

object ChartView {

  @js.native
  @JSImport("chart.js/auto", JSImport.Default)
  class Chart(ctx: dom.Element, config: js.Dynamic) extends js.Object {}

  def create(): HtmlElement = {

    val dataset = js.Dynamic.literal(
      label = "demo",
      data = js.Array(2, 4, 6)
    )

    val config = js.Dynamic.literal(
      `type` = "bar",
      data = js.Dynamic.literal(
        labels = js.Array("Foo", "Bar", "Baz"),
        datasets = js.Array(dataset)
      )
    )

    div(
      cls := "bg-gray-900 text-gray-50 rounded my-3 p-3 h-60 flex",
      div(
        cls := "w-1/2 h-full rounded bg-gray-50 p-3",
        div(
          div(
            cls := "h-full",
            position.relative,
            canvas(
              width("100%"),
              height("100%"),
              onMountCallback { el =>
                val _ = new Chart(el.thisNode.ref, config)
              }
            )
          )
        )
      ),
      div(
        cls := "w-1/2 h-full p-3 ml-2",
        span(
          cls := "text-2xl font-bold",
          "Some Diagram info"
        )
      )
    )
  }
}
