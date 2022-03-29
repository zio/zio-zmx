package zio.zmx.client.frontend.d3v7

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import org.scalajs.dom

@js.native
@JSImport("d3-selection", JSImport.Namespace)
object d3Selection extends js.Object {
  def select(selector: String): Selection[dom.EventTarget] = js.native

  @js.native
  trait BaseDom[Datum, T <: BaseDom[Datum, T]] extends js.Object {
    type ValueFunction0[Return] = js.Function0[Return]
    type ValueFunction1[Return] = js.Function1[Datum, Return]
    type ValueFunction2[Return] = js.Function2[Datum, Index, Return]
    type ValueFunction3[Return] = js.Function3[Datum, Index, Group, Return]

    type ListenerFunction0 = ValueFunction0[Unit]
    type ListenerFunction1 = ValueFunction1[Unit]
    type ListenerFunction2 = ValueFunction2[Unit]
    type ListenerFunction3 = ValueFunction3[Unit]

    type ValueThisFunction0[C <: CurrentDom, Return] = js.ThisFunction0[C, Return]
    type ValueThisFunction1[C <: CurrentDom, Return] = js.ThisFunction1[C, Datum, Return]
    type ValueThisFunction2[C <: CurrentDom, Return] = js.ThisFunction2[C, Datum, Index, Return]
    type ValueThisFunction3[C <: CurrentDom, Return] = js.ThisFunction3[C, Datum, Index, Group, Return]

    type ListenerThisFunction0[C <: CurrentDom] = ValueThisFunction0[C, Unit]
    type ListenerThisFunction1[C <: CurrentDom] = ValueThisFunction1[C, Unit]
    type ListenerThisFunction2[C <: CurrentDom] = ValueThisFunction2[C, Unit]
    type ListenerThisFunction3[C <: CurrentDom] = ValueThisFunction3[C, Unit]

    def style(name: String, value: String): T               = js.native
    def style[R](name: String, value: ValueFunction0[R]): T = js.native
    def style[R](name: String, value: ValueFunction1[R]): T = js.native
    def style[R](name: String, value: ValueFunction2[R]): T = js.native
    def style[R](name: String, value: ValueFunction3[R]): T = js.native

    def style[R](name: String, value: ValueThisFunction0[CurrentDom, R]): T =
      js.native // no C type parameter here, since it would be ambiguous with ValueFunction1. TODO: is there a better solution?
    def style[C <: CurrentDom, R](name: String, value: ValueThisFunction1[C, R]): T = js.native
    def style[C <: CurrentDom, R](name: String, value: ValueThisFunction2[C, R]): T = js.native
    def style[C <: CurrentDom, R](name: String, value: ValueThisFunction3[C, R]): T = js.native

    def classed(names: String, value: Boolean): T                 = js.native
    def classed(names: String, value: ValueFunction0[Boolean]): T = js.native
    def classed(names: String, value: ValueFunction1[Boolean]): T = js.native
    def classed(names: String, value: ValueFunction2[Boolean]): T = js.native
    def classed(names: String, value: ValueFunction3[Boolean]): T = js.native

    def attr(name: String, value: String): T               = js.native
    def attr(name: String, value: Double): T               = js.native
    def attr(name: String, value: Boolean): T              = js.native
    def attr[R](name: String, value: ValueFunction1[R]): T = js.native
    def attr[R](name: String, value: ValueFunction2[R]): T = js.native

    def text(): String                       = js.native
    def text(value: String): T               = js.native
    def text[R](value: ValueFunction0[R]): T = js.native
    def text[R](value: ValueFunction1[R]): T = js.native
    def text[R](value: ValueFunction2[R]): T = js.native

    def html(): String                       = js.native
    def html(value: String): T               = js.native
    def html[R](value: ValueFunction0[R]): T = js.native
    def html[R](value: ValueFunction1[R]): T = js.native

    def call(func: js.Function, args: js.Any*): T = js.native
    def remove(): T                               = js.native
  }

  @js.native
  trait BaseSelection[Datum, T <: BaseSelection[Datum, T]] extends BaseDom[Datum, T] {
    def append(`type`: String): T                               = js.native
    def append(`type`: js.Function0[dom.EventTarget]): T        = js.native
    def append(`type`: js.Function1[Datum, dom.EventTarget]): T = js.native

    def on(typenames: String, listener: ListenerFunction0): T = js.native
    def on(typenames: String, listener: ListenerFunction1): T = js.native
    def on(typenames: String, listener: ListenerFunction2): T = js.native

    def data(): js.Array[Datum]                                                                        = js.native
    def data[NewDatum <: Datum](data: js.Array[NewDatum]): Update[NewDatum]                            = js.native
    // TODO: d3 doc says that key can be a ThisFunction with this as the current node. It Doesn't work here...
    def data[NewDatum <: Datum, R](data: js.Array[NewDatum], key: ValueFunction0[R]): Update[NewDatum] = js.native
    def data[NewDatum <: Datum, R](data: js.Array[NewDatum], key: ValueFunction1[R]): Update[NewDatum] = js.native
    def data[NewDatum <: Datum, R](data: js.Array[NewDatum], key: ValueFunction2[R]): Update[NewDatum] = js.native
    def data[NewDatum <: Datum, R](data: js.Array[NewDatum], key: ValueFunction3[R]): Update[NewDatum] = js.native

    def each[C <: CurrentDom](function: ListenerThisFunction0[C]): Unit = js.native
    def each[C <: CurrentDom](function: ListenerThisFunction1[C]): Unit = js.native
    def each[C <: CurrentDom](function: ListenerThisFunction2[C]): Unit = js.native
    def each[C <: CurrentDom](function: ListenerThisFunction3[C]): Unit = js.native

    def size(): Int = js.native
  }

  @js.native
  trait Selection[Datum] extends BaseSelection[Datum, Selection[Datum]] {
    def select[SelData](selector: String): Selection[SelData]    = js.native
    def selectAll[SelData](selector: String): Selection[SelData] = js.native
    def node(): dom.EventTarget                                  = js.native

    /** @see [[d3transition]] */
    // def transition(): Transition[Datum] = js.native

    /** @see [[d3transition]] */
    // def transition(name: String): Transition[Datum] = js.native
  }

  @js.native
  trait Update[Datum] extends BaseSelection[Datum, Update[Datum]] {
    def enter(): Enter[Datum]    = js.native
    def exit(): Selection[Datum] = js.native
  }

  @js.native
  trait Enter[Datum] extends js.Object {
    def append(name: String): Selection[Datum]                                 = js.native
    def append(`type`: js.Function0[dom.EventTarget]): Selection[Datum]        = js.native
    def append(`type`: js.Function1[Datum, dom.EventTarget]): Selection[Datum] = js.native

    def append(name: js.Function3[Datum, Double, Double, dom.EventTarget]): Selection[Datum] = js.native

    def size(): Int = js.native
  }
}
