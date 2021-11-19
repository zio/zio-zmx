package zio.zmx.client.frontend.components

import zio.metrics.MetricKey

import com.raquo.laminar.api.L._

object MetricBadge {

  def render(key: MetricKey): HtmlElement =
    new MetricBadgeImpl(key).render

  private class MetricBadgeImpl(key: MetricKey) {

    def render: HtmlElement = ???
  }
}
