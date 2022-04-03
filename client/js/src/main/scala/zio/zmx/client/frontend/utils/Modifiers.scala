package zio.zmx.client.frontend.utils

import com.raquo.domtypes.generic.codecs.StringAsIsCodec
import com.raquo.laminar.DomApi
import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.KeyUpdater

import zio.zmx.client.frontend.components.Theme

object Modifiers {

  private object propDataTheme extends HtmlAttr[String]("data-theme", StringAsIsCodec)
  private object propDataTip   extends HtmlAttr[String]("data-tip", StringAsIsCodec)

  def displayWhen($isVisible: Observable[Boolean]): Mod[HtmlElement] =
    display <-- $isVisible.map(if (_) null else "none")

  def dataTheme($theme: Observable[Theme.DaisyTheme]): Mod[HtmlElement] =
    new KeyUpdater[HtmlElement, HtmlAttr[String], String](
      propDataTheme,
      $theme.map(_.name),
      (element, nextValue) => DomApi.setHtmlAttribute(element, propDataTheme, nextValue),
    )

  def dataTip($tip: Observable[String]): Mod[HtmlElement] =
    new KeyUpdater[HtmlElement, HtmlAttr[String], String](
      propDataTip,
      $tip,
      (element, nextValue) => DomApi.setHtmlAttribute(element, propDataTip, nextValue),
    )
}
