package zio.zmx.client.frontend.views

import scalajs.js
import org.scalajs.dom
import org.scalajs.dom.html.Canvas
import scalajs.js.annotation.JSImport
import com.raquo.laminar.api.L._
import java.time.Instant
import com.raquo.laminar.nodes.ReactiveHtmlElement
import scala.collection.mutable

@js.native
@JSImport("chart.js/auto", JSImport.Default)
class Chart(ctx: dom.Element, config: js.Dynamic) extends js.Object {
  def update(mode: js.UndefOr[String]): Unit = js.native
}

object ChartView {

  final private case class TimeSeries(
    label: String,
    color: String,
    key: String,
    tension: Double = 0,
    data: js.Array[js.Dynamic] = js.Array(),
    maxSize: Int = 100
  ) {
    def recordData(when: Instant, value: Double): js.Array[js.Dynamic] = {
      val xx  = when.toEpochMilli().doubleValue()
      println(s"($xx, $value)")
      if (data.size == maxSize) {
        data.shift()
      }
      val cnt = data.push(
        js.Dynamic.literal(
          x = xx,
          y = value
        )
      )
      println(s"TimeSeries [$key] has now [$cnt] values.")
      data
    }

    def asDataSet: js.Dynamic =
      js.Dynamic.literal(
        label = label,
        borderColor = color,
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
        maintainAspectRatio = false,
        scales = js.Dynamic.literal(
          x = js.Dynamic.literal(
            `type` = "timeseries"
          )
        )
      ),
      data = {
        val ds = series.view.values.map(_.asDataSet).toSeq
        println(s"Mounting chart view with [${ds.size}] datasets.")
        js.Dynamic.literal(datasets = js.Array(ds: _*))
      }
    )

    def addTimeseries(key: String, color: String, tension: Double = 0, maxSize: Int = 100): Unit =
      chart.foreach { c =>
        if (series.get(key).isEmpty) {
          println(s"Adding TimeSeries [$key]")
          val ts = TimeSeries(key, color, key, tension, js.Array[js.Dynamic](), maxSize)
          val _  = series.put(ts.key, ts)
          println(s"Adding TimeSeries [$key] to diagram")
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

    def mount(canvas: ReactiveHtmlElement[Canvas]) =
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
