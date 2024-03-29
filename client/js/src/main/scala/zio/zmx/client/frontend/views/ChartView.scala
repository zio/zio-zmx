package zio.zmx.client.frontend.views

import scalajs.js
import org.scalajs.dom
import org.scalajs.dom.html.Canvas

import scalajs.js.annotation.JSImport
import com.raquo.laminar.api.L._

import java.time.Instant
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.ext.Color

import scala.collection.mutable

@js.native
@JSImport("chart.js/auto", JSImport.Default)
class Chart(ctx: dom.Element, config: js.Dynamic) extends js.Object {
  def update(mode: js.UndefOr[String]): Unit = js.native
}

object ChartView {

  final private case class TimeSeries(
    label: String,
    color: Color,
    key: String,
    tension: Double = 0,
    data: js.Array[js.Dynamic] = js.Array(),
    maxSize: Int = 100
  ) {
    def recordData(when: Instant, value: Double): Unit = {
      if (data.size == maxSize) {
        data.shift()
      }
      val _ = data.push(
        js.Dynamic.literal(
          x = when.toEpochMilli().doubleValue(),
          y = value
        )
      )
    }

    def asDataSet: js.Dynamic =
      js.Dynamic.literal(
        label = label,
        borderColor = color.toHex,
        fill = false,
        tension = tension,
        data = data
      )
  }

  final case class ChartView() {

    private val series: mutable.Map[String, TimeSeries] = mutable.Map.empty

    {
      val _ = ScalaDateAdapter.install()
    }

    private val options: js.Dynamic = js.Dynamic.literal(
      `type` = "line",
      options = js.Dynamic.literal(
        parsing = false,
        animation = true,
        maintainAspectRatio = false,
        scales = js.Dynamic.literal(
          x = js.Dynamic.literal(
            `type` = "timeseries"
          )
        )
      ),
      data = {
        val ds = series.view.values.map(_.asDataSet).toSeq
        js.Dynamic.literal(datasets = js.Array(ds: _*))
      }
    )

    def addTimeseries(key: String, color: Color, tension: Double = 0, maxSize: Int = 100): Unit =
      chart.foreach { c =>
        if (!series.contains(key)) {
          val ts = TimeSeries(key, color, key, tension, js.Array[js.Dynamic](), maxSize)
          val _  = series.put(ts.key, ts)
          c
            .asInstanceOf[js.Dynamic]
            .selectDynamic("data")
            .selectDynamic("datasets")
            .asInstanceOf[js.Array[js.Dynamic]]
            .push(ts.asDataSet)
          update()
        }
      }

    def recordData(key: String, when: Instant, value: Double): Unit = {
      series.get(key).foreach(ts => ts.recordData(when, value))
      update()
    }

    def mount(canvas: ReactiveHtmlElement[Canvas]): Unit =
      chart = Some(new Chart(canvas.ref, options))

    def update(): Unit =
      chart.foreach(_.update(()))

    def element(): HtmlElement =
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
                  mount(el.thisNode)
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

    private var chart: Option[Chart] = None
  }

}
