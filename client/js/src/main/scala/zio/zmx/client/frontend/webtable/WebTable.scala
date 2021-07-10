package zio.zmx.client.frontend.webtable

import zio.zmx.client.frontend.utils.Implicits._

import com.raquo.laminar.api.L._
import zio._

import magnolia._
import scala.language.experimental.macros

trait WebTable[K, A] {

  def render(v: Signal[Chunk[A]]): HtmlElement =
    div(
      cls := "rounded p-3 my-3 bg-gray-900",
      table(
        cls := "text-gray-50",
        thead(
          webrow.headers.map(h => th(h.capitalize))
        ),
        tbody(
          children <-- v.split(rowKey)(renderRow)
        )
      )
    )

  private def renderRow(key: K, data: A, s: Signal[A]): HtmlElement =
    tr(
      children <-- s.map(webrow.cells).map(data => (data ++ extraCols(key)).map(inner => td(inner)))
    )

  def rowKey: A => K
  def webrow: WebTable.WebRow[A]
  def extraCols: K => Chunk[HtmlElement] = _ => Chunk.empty

}

object WebTable {

  def create[K, A](wr: WebRow[A], rk: A => K, extra: K => Chunk[HtmlElement]): WebTable[K, A] =
    new WebTable[K, A] {
      override def rowKey    = rk
      override def webrow    = wr
      override def extraCols = extra
    }

  trait WebRow[-A] { self =>
    def ++[B](that: WebRow[B]): WebRow[(A, B)] = new WebRow[(A, B)] {
      override def cells(v: (A, B))       = self.cells(v._1) ++ that.cells(v._2)
      override def headers: Chunk[String] = self.headers ++ that.headers
    }

    def rmap[B](f: B => A): WebRow[B] = new WebRow[B] {
      override def cells(b: B): Chunk[HtmlElement] = self.cells(f(b))
      override def headers: Chunk[String]          = self.headers
    }

    def headers: Chunk[String] = Chunk.empty
    def cells(v: A): Chunk[HtmlElement]
  }

  object WebRow {

    implicit val string: WebRow[String] = new WebRow[String] {
      override def cells(v: String): Chunk[HtmlElement] = Chunk(
        span(v)
      )
    }

    implicit val int: WebRow[Int] = new WebRow[Int] {
      override def cells(v: Int): Chunk[HtmlElement] = Chunk(
        span(v.toString())
      )
    }

    implicit val long: WebRow[Long] = new WebRow[Long] {
      override def cells(v: Long): Chunk[HtmlElement] = Chunk(
        span(v.toString())
      )
    }

    implicit val double: WebRow[Double] = new WebRow[Double] {
      override def cells(v: Double): Chunk[HtmlElement] = Chunk(
        span(v.toString())
      )
    }
  }

  object DeriveRow {

    type Typeclass[A] = WebRow[A]

    def combine[A](caseClass: CaseClass[WebRow, A]): WebRow[A] = new WebRow[A] {
      override def cells(v: A): Chunk[HtmlElement] =
        Chunk.fromIterable(caseClass.parameters.flatMap(p => p.typeclass.cells(p.dereference(v))))
      override def headers: Chunk[String]          =
        Chunk.fromIterable(caseClass.parameters.flatMap { p =>
          val hds = p.typeclass.headers
          // When we are at the leaf of a case class we are collecting the label, otherwise we
          // use the headers generated for this particular parameter
          if (hds.isEmpty) Chunk(p.label) else hds
        })
    }

    implicit def gen[A]: WebRow[A] = macro Magnolia.gen[A]
  }
}
