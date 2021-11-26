package zio.zmx.client.frontend.views

import zio.Chunk
import com.raquo.laminar.api.L._
import zio.metrics.MetricKey

import zio.zmx.client.frontend.utils.Implicits._

final case class MetricsSelector(lbl: String, observer: Observer[MetricKey]) {

  def render($metrics: Signal[Chunk[MetricKey]], separator: Boolean = false): HtmlElement =
    div(
      cls := "flex flex-wrap",
      child <-- $metrics.map {
        case Chunk.empty => emptyNode
        case metrics     =>
          div(
            cls := "w-full form-control" + (if (separator) " border-b-2" else ""),
            label(cls := "label", span(cls := "label-text", lbl)),
            div(
              cls := "flex flex-wrap",
              metrics.map(m =>
                div(
                  cls := "badge badge-info text-xl p-4 m-3",
                  m.longName,
                  onClick.map(_ => m) --> observer
                )
              )
            )
          )
      }
    )
}
