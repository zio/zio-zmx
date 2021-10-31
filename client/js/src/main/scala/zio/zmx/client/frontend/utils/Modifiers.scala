package zio.zmx.client.frontend.utils

import com.raquo.laminar.api.L._

object Modifiers {

  def displayWhen($isVisible: Observable[Boolean]): Mod[HtmlElement] =
    display <-- $isVisible.map(if (_) null else "none")
}
