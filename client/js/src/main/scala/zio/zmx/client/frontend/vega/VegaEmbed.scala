package zio.zmx.client.frontend.vega

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import org.scalajs.dom.Element

object VegaEmbed {

  @js.native
  @JSImport("vega-embed", JSImport.Default)
  def embed(el: Element, vegaDef: js.Dynamic, options: js.Dynamic): js.Promise[js.Dynamic] =
    js.native
}
