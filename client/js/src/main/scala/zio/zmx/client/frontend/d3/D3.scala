package zio.zmx.client.frontend.d3

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("d3", JSImport.Namespace)
object d3 extends js.Object {

  val version: String = js.native

  def select(selector: String): Selection = js.native
}

@js.native
trait Selection extends js.Object {

  def size(): js.Any = js.native
}
