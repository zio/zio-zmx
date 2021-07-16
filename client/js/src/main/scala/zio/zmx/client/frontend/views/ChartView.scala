package zio.zmx.client.frontend.views

import scalajs.js
import org.scalajs.dom
import scalajs.js.annotation.JSImport
import com.raquo.laminar.api.L._
import java.time.Instant
import java.time.temporal.ChronoUnit

object ChartView {

  @js.native
  @JSImport("chart.js/auto", JSImport.Default)
  class Chart(ctx: dom.Element, config: js.Dynamic) extends js.Object {}

  ScalaDateAdapter.install()

  def create(): HtmlElement = {

    val now  = Instant.now()
    val day1 = now.minus(1L, ChronoUnit.DAYS)
    val day2 = now.minus(2L, ChronoUnit.DAYS)

    val dataset1 = js.Dynamic.literal(
      label = "demo",
      data = js.Array(
        js.Dynamic.literal(x = day2.toEpochMilli().doubleValue(), y = 2),
        js.Dynamic.literal(x = day1.toEpochMilli().doubleValue(), y = 4),
        js.Dynamic.literal(x = now.toEpochMilli().doubleValue(), y = 6)
      ),
      borderColor = "#00DD00",
      fill = false,
      tension = 0.6
    )

    val dataset2 = js.Dynamic.literal(
      label = "values",
      data = js.Array(
        js.Dynamic.literal(x = (day2.minus(1L, ChronoUnit.DAYS)).toEpochMilli().doubleValue(), y = 3),
        js.Dynamic.literal(x = (day1.minus(1L, ChronoUnit.DAYS)).toEpochMilli().doubleValue(), y = 7),
        js.Dynamic.literal(x = (now.minus(1L, ChronoUnit.DAYS)).toEpochMilli().doubleValue(), y = 1)
      ),
      borderColor = "#3000DD",
      fill = false,
      tension = 0.6
    )

    val dataset3 = js.Dynamic.literal(
      label = "something",
      data = js.Array(
        js.Dynamic.literal(x = (day2.minus(1L, ChronoUnit.DAYS)).toEpochMilli().doubleValue(), y = 8),
        js.Dynamic.literal(x = (day1.minus(1L, ChronoUnit.DAYS)).toEpochMilli().doubleValue(), y = 5),
        js.Dynamic.literal(x = (now.minus(1L, ChronoUnit.DAYS)).toEpochMilli().doubleValue(), y = 9)
      ),
      borderColor = "#DD0000",
      fill = false,
      tension = 0.6
    )

    val config = js.Dynamic.literal(
      `type` = "line",
      options = js.Dynamic.literal(
        parsing = false,
        maintainAspectRatio = false,
        scales = js.Dynamic.literal(
          x = js.Dynamic.literal(
            `type` = "timeseries"
          )
        )
      ),
      data = js.Dynamic.literal(
        // labels = js.Array("Foo", "Bar", "Baz"),
        datasets = js.Array(dataset1, dataset2, dataset3)
      )
    )

    div(
      cls := "bg-gray-900 text-gray-50 rounded my-3 p-3 h-80 flex",
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
