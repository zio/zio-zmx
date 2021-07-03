package zio.zmx.client.frontend.webtable

import zio.zmx.client.frontend.utils.Implicits._

import com.raquo.laminar.api.L._
import zio._

sealed trait WebTable[K, A] {

  private def renderRow(key: K, data: A, s: Signal[A]): HtmlElement =
    tr(
      children <-- s.map(webrow.cells)
    )

  def render(v: Signal[Chunk[A]]): HtmlElement = table(
    thead(
      webrow.headers.map(h => th(h.capitalize))
    ),
    tbody(
      children <-- v.split(rowKey)(renderRow)
    )
  )

  def webrow: WebTable.WebRow[A]
  def rowKey: A => K
}

object WebTable {
  def string(h: String) = new WebTable[Int, String] {
    def webrow: WebRow[String] = WebRow.string(h)
    def rowKey: String => Int  = _.hashCode()
  }

  def int(h: String) = new WebTable[Int, Int] {
    def webrow: WebRow[Int] = WebRow.int(h)
    def rowKey: Int => Int  = identity
  }

  def double(h: String) = new WebTable[Int, Double] {
    def webrow: WebRow[Double] = WebRow.double(h)
    def rowKey: Double => Int  = _.hashCode()
  }

  def example = new WebTable[String, CounterInfo] {
    def webrow: WebRow[CounterInfo]   = WebRow.example
    def rowKey: CounterInfo => String = _.longName
  }

  private[WebTable] sealed trait WebRow[A] { self =>
    def ++[B](that: WebRow[B]): WebRow[(A, B)] = new WebRow[(A, B)] {
      override def cells(v: (A, B)) = self.cells(v._1) ++ that.cells(v._2)
      def headers: Chunk[String]    = self.headers ++ that.headers
    }

    def rmap[B](f: B => A)(h: String => String = identity): WebRow[B] = new WebRow[B] {
      override def cells(b: B): Chunk[HtmlElement] = self.cells(f(b))

      override def headers: Chunk[String] = self.headers.map(h)
    }

    def headers: Chunk[String]
    def cells(v: A): Chunk[HtmlElement]
  }

  private[WebTable] object WebRow {

    def string(h: String): WebRow[String] = new WebRow[String] {
      override def cells(v: String): Chunk[HtmlElement] = Chunk(
        td(v)
      )

      override def headers: Chunk[String] = Chunk(h)
    }

    def int(h: String): WebRow[Int] = new WebRow[Int] {
      override def cells(v: Int): Chunk[HtmlElement] = Chunk(
        td(v.toString())
      )
      def headers: Chunk[String]                     = Chunk(h)
    }

    def double(h: String): WebRow[Double] = new WebRow[Double] {
      override def cells(v: Double): Chunk[HtmlElement] = Chunk(
        td(v.toString())
      )
      def headers: Chunk[String]                        = Chunk(h)
    }

    val example: WebRow[CounterInfo] = (string("name") ++ string("labels") ++ double("current")).rmap[CounterInfo](ci =>
      ((ci.name, ci.labels.mkString(",")), ci.current)
    )()
  }

}

final case class CounterInfo(
  name: String,
  labels: Chunk[String],
  current: Double
) {
  val longName = s"$name:${labels.mkString(",")}"
}
