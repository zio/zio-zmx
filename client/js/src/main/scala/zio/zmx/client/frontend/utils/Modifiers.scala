package zio.zmx.client.frontend.utils

import com.raquo.laminar.api.L._
import com.raquo.domtypes.generic.codecs.StringAsIsCodec
import zio.zmx.client.frontend.state.Theme
import com.raquo.laminar.modifiers.KeyUpdater
import com.raquo.laminar.DomApi

object Modifiers {

  private object propDataTheme extends HtmlAttr[String]("data-theme", StringAsIsCodec)

  def displayWhen($isVisible: Observable[Boolean]): Mod[HtmlElement] =
    display <-- $isVisible.map(if (_) null else "none")

  def dataTheme($theme: Observable[Theme.DaisyTheme]): Mod[HtmlElement] =
    new KeyUpdater[HtmlElement, HtmlAttr[String], String](
      propDataTheme,
      $theme.map(_.name),
      (element, nextValue) => DomApi.setHtmlAttribute(element, propDataTheme, nextValue)
    )
}
