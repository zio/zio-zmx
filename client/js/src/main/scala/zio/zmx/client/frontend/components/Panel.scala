package zio.zmx.client.frontend.components

import com.raquo.domtypes.generic.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement

import com.raquo.laminar.api.L._

object Panel {
  def apply(mods: Modifier[HtmlElement]*): ReactiveHtmlElement[org.scalajs.dom.html.Div] =
    div(
      cls := "px-3 mt-3 w-full",
      div(
        cls := LayoutConstants.layoutPanel,
        mods
      )
    )
}
