package zio.zmx.client.frontend.views

import zio.Chunk
import com.raquo.laminar.api.L._
import zio.metrics.MetricKey

import zio.zmx.client.frontend.utils.Implicits._

class MetricsView(observer: Observer[MetricKey]) {

  def render($metrics: Signal[Chunk[MetricKey]]): HtmlElement =
    div(
      children <-- $metrics.split(identity)(renderBadge)
    )

  private def renderBadge(k: MetricKey, m: MetricKey, $sig: Signal[MetricKey]): HtmlElement =
    div(
      child <-- $sig.map(metric =>
        div(
          cls := "badge badge-info p-1 m-1",
          metric.longName,
          onClick.map(_ => metric) --> observer
        )
      )
    )
}
