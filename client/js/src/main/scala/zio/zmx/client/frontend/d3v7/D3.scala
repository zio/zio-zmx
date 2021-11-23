package zio.zmx.client.frontend

import scala.language.implicitConversions
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import org.scalajs.dom

package d3v7 {

  @js.native
  @JSImport("d3", JSImport.Namespace)
  object d3 extends js.Object {

    val version: String = js.native
  }

}

package object d3v7 {

  @inline implicit def d3toD3Selection(d3t: d3.type): d3v7.d3Selection.type = d3v7.d3Selection
  @inline implicit def d3toD3Shape(d3t: d3.type): d3v7.d3Shape.type         = d3v7.d3Shape

  type CurrentDom = dom.EventTarget
  type Index      = Int
  type Group      = js.UndefOr[Int]

}
