package zio.zmx.client.frontend.views

import scalajs.js
import org.scalajs.dom
import org.scalajs.dom.html.Canvas
import scalajs.js.annotation.JSImport
import com.raquo.laminar.api.L._
import java.time.Instant
import java.time.temporal.ChronoUnit
import com.raquo.laminar.nodes.ReactiveHtmlElement
import scala.collection.mutable
import scala.util.Random

@js.native
@JSImport("chart.js/auto", JSImport.Default)
class Chart(ctx: dom.Element, config: js.Dynamic) extends js.Object {
  def update(mode: js.UndefOr[String]): Unit = js.native
}

final case class ChartView(
  canvas: ReactiveHtmlElement[Canvas]
) {

  private val series: mutable.Map[String, TimeSeries] = mutable.Map.empty

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
    data = js.Dynamic.literal(
      datasets = js.Array[js.Dynamic]()
    )
  )

  def addTimeseries(ts: TimeSeries): Unit =
    if (series.get(ts.key).isEmpty) {
      val _ = series.put(ts.key, ts)
      chart
        .asInstanceOf[js.Dynamic]
        .selectDynamic("data")
        .selectDynamic("datasets")
        .asInstanceOf[js.Array[js.Dynamic]]
        .push(ts.asDataSet)
      update()
    }

  def recordData(key: String, when: Instant, value: Double): Unit = {
    series.get(key).foreach(ts => ts.recordData(when, value))
    update()
  }

  def update(): Unit =
    chart.update(())

  lazy val chart = new Chart(canvas.ref, options)
}

final case class TimeSeries(
  label: String,
  color: String,
  key: String,
  tension: Double = 0,
  data: js.Array[js.Dynamic] = js.Array(),
  maxSize: Int = 100
) {
  def recordData(when: Instant, value: Double) = {
    val xx  = when.toEpochMilli().doubleValue()
    println(s"($xx, $value)")
    val cnt = data.push(
      js.Dynamic.literal(
        x = xx,
        y = value
      )
    )
    println(s"TimeSeries [$key] has now [$cnt] values. $dumpData")
  }

  private def dumpData: String =
    data.map { entry =>
      val x = entry.selectDynamic("x").asInstanceOf[Double]
      val y = entry.selectDynamic("y").asInstanceOf[Double]
      s"[($x, $y)]"
    }.mkString(",")

  def asDataSet: js.Dynamic = {
    println(s"Current data pushed to Chart is $dumpData")
    js.Dynamic.literal(
      label = label,
      borderColor = color,
      fill = false,
      tension = tension,
      data = data
    )
  }
}

object ChartView {

  private lazy val adapterInstalled = ScalaDateAdapter.install()

  def create(): HtmlElement = {
    val _ = adapterInstalled

    val element = div(
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
                val _ = new ChartView(el.thisNode).chart
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

    element
  }
}
