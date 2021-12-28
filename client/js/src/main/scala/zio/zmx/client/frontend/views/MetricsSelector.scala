package zio.zmx.client.frontend.views

import zio.Chunk
import com.raquo.laminar.api.L._
import zio.metrics.MetricKey

import zio.zmx.client.frontend.utils.Implicits._

final case class MetricsSelector(lbl: String, observer: Observer[MetricKey], display: String = "primary") {

  def render($metrics: Signal[Chunk[MetricKey]]): HtmlElement =
    div(
      cls := "flex flex-wrap",
      child <-- $metrics.map {
        case Chunk.empty => emptyNode
        case metrics     =>
          div(
            cls := s"card w-full bg-$display text-$display-content rounded bordered p-4 mt-2 form-control",
            div(
              cls := "card-body",
              h2(
                cls := "card-title",
                lbl
              ),
              div(
                cls := "flex flex-wrap",
                metrics.map(m =>
                  div(
                    cls := "badge badge-info text-xl cursor-pointer p-4 m-3",
                    m.longName,
                    onClick.map(_ => m) --> observer
                  )
                )
              )
            )
          )
      }
    )
}
