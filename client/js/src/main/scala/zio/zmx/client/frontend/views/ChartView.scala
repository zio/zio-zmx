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
//      backgroundColor = "rgb(255,99,132)",
//      borderColor = "rgb(10,10,10)",
      data = js.Array(2, 4, 6)
    )

    val config = js.Dynamic.literal(
      `type` = "line",
      data = js.Dynamic.literal(
        labels = js.Array("Foo", "Bar", "Baz"),
        datasets = js.Array(dataset)
      )
    )

    div(
      position.relative,
      width("90vw"),
      height("40vh"),
      background("#fff"),
      canvas(
        width("100%"),
        height("100%"),
        onMountCallback { el =>
          val _ = new Chart(el.thisNode.ref, config)
        }
      )
    )
  }
}
