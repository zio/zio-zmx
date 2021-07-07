package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._
import zio.zmx.client.MetricsMessage
import zio.zmx.client.frontend.AppState

sealed trait DiagramView {
  def render(): HtmlElement
}

object DiagramView {

  def counterDiagram(key: String): DiagramView =
    new DiagramViewImpl[MetricsMessage.CounterChange](key, AppState.counterMessages.signal)

  private class DiagramViewImpl[M <: MetricsMessage](key: String, events: Signal[Option[M]]) extends DiagramView {

    override def render(): HtmlElement = div(
      div(
        s"A diagram for $key",
        p(child <-- events.signal.map(_.toString()))
      )
    )
  }
}
