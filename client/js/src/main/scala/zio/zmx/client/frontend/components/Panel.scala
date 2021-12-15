package zio.zmx.client.frontend.components

import com.raquo.domtypes.generic.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement

import com.raquo.laminar.api.L._

object Panel {
  def apply(mods: Modifier[HtmlElement]*): ReactiveHtmlElement[org.scalajs.dom.html.Div] =
    div(
      cls := "bg-neutral text-neutral-content rounded-box text-lg font-bold",
      mods
    )
}
