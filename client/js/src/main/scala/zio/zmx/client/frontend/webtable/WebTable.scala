package zio.zmx.client.frontend.webtable

import zio.zmx.client.frontend.utils.Implicits._

import com.raquo.laminar.api.L._
import zio._

import magnolia._
import scala.language.experimental.macros

sealed trait WebTable[A] {

  def render(v: Signal[Chunk[A]]): HtmlElement = table(
    thead(
      webrow.headers.map(h => th(h.capitalize))
    ),
    tbody(
      children <-- v.split(rowKey)(renderRow)
    )
  )

  private def renderRow(key: Int, data: A, s: Signal[A]): HtmlElement =
    tr(
      children <-- s.map(webrow.cells)
    )

  private def rowKey: A => Int = _.toString().hashCode()

  def webrow: WebTable.WebRow[A]

}

object WebTable {

  private[webtable] trait WebRow[-A] { self =>
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

  private[webtable] object WebRow {

    implicit val string: WebRow[String] = new WebRow[String] {
      override def cells(v: String): Chunk[HtmlElement] = Chunk(
        td(v)
      )
    }

    implicit val int: WebRow[Int] = new WebRow[Int] {
      override def cells(v: Int): Chunk[HtmlElement] = Chunk(
        td(v.toString())
      )
    }

    implicit val double: WebRow[Double] = new WebRow[Double] {
      override def cells(v: Double): Chunk[HtmlElement] = Chunk(
        td(v.toString())
      )
    }
  }

  private[webtable] object DeriveRow {

    type Typeclass[A] = WebRow[A]

    def combine[A](caseClass: CaseClass[WebRow, A]): WebRow[A] = new WebRow[A] {
      override def cells(v: A): Chunk[HtmlElement] =
        Chunk.fromIterable(caseClass.parameters.flatMap(p => p.typeclass.cells(p.dereference(v))))
      override def headers: Chunk[String]          =
        Chunk.fromIterable(caseClass.parameters.flatMap { p =>
          val hds = p.typeclass.headers
          if (hds.isEmpty) Chunk(p.label) else hds
        })
    }

    implicit def gen[A]: WebRow[A] = macro Magnolia.gen[A]
  }
}

final case class CounterInfo(
  name: String,
  labels: String,
  current: Double
) {
  val longName = s"$name:${labels.mkString(",")}"
}

object CounterInfo {
  val webTable: WebTable[CounterInfo] = {
    val wr = WebTable.DeriveRow.gen[CounterInfo]
    new WebTable[CounterInfo] {
      def webrow: WebTable.WebRow[CounterInfo] = wr
    }
  }
}
