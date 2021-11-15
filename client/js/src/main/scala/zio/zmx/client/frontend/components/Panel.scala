package zio.zmx.client.frontend.components

import com.raquo.domtypes.generic.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import LayoutConstants._

import com.raquo.laminar.api.L._

object Panel {
  def apply(mods: Modifier[HtmlElement]*): ReactiveHtmlElement[org.scalajs.dom.html.Div] =
    div(
      cls := s"px-3 mt-3 w-full $layoutPanel",
      mods
    )
}
