package zio.zmx.client.frontend.views

import zio.Chunk
import com.raquo.laminar.api.L._
import zio.zmx.client.frontend.model.MetricInfo
import zio.metrics.MetricKey

import zio.zmx.client.frontend.utils.Implicits._

class MetricsView(observer: Observer[MetricInfo]) {

  def render($metrics: Signal[Chunk[MetricInfo]]): HtmlElement =
    div(
      children <-- $metrics.split(_.metric)(renderBadge)
    )

  private def renderBadge(k: MetricKey, info: MetricInfo, $sig: Signal[MetricInfo]): HtmlElement =
    div(
      child <-- $sig.map(sig =>
        div(
          cls := "badge badge-info p-1",
          info.metric.longName,
          onClick.map(_ => sig) --> observer
        )
      )
    )
}
