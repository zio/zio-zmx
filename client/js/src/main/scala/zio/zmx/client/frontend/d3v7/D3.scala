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

  import zio.zmx.client.frontend.d3v7.d3Selection.Selection

  @inline implicit def d3toD3Axis(d3t: d3.type): d3Axis.type           = d3Axis
  @inline implicit def d3toD3Selection(d3t: d3.type): d3Selection.type = d3Selection
  @inline implicit def d3toD3Scale(d3t: d3.type): d3Scale.type         = d3Scale
  @inline implicit def d3toD3Shape(d3t: d3.type): d3Shape.type         = d3Shape
  @inline implicit def d3toD3Time(d3t: d3.type): d3Time.type           = d3Time
  implicit class D3Ops(self: d3.type) {

    /**
     * Convenience method for 'drilling down' along some selections
     */
    def selectPath(top: String, path: String*): Selection[dom.EventTarget] =
      path.foldLeft(self.select(top)) { case (cur, s) => cur.select(s) }
  }

  implicit class D3SelectionOps[T](self: Selection[T]) {

    /**
     * Convenience method for removing elements from a selection
     * It will return a handle to the modified selection to allow for a fluid DSL.
     */
    def removeBy(what: String*): Selection[T] = {
      what.foreach { s =>
        self
          .selectAll(s)
          .data(js.Array[Any]())
          .exit()
          .remove()
      }

      self
    }

    /**
     * Convenience method for adding something to a selection and then return a handle to
     * the same collection. Useful for adding multiple groups on the same level (as document siblings)
     */
    def enhance(f: Selection[T] => Selection[T]): Selection[T] = {
      val _ = f(self)
      self
    }
  }

  type CurrentDom = dom.EventTarget
  type Index      = Int
  type Group      = js.UndefOr[Int]

}
