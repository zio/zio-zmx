package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._
import zio._

trait WebTable[K, A] {

  // The top level render method to include the entire table in the page
  def render: HtmlElement =
    div(
      cls := "rounded p-3 my-3 bg-gray-900",
      data --> Observer[A](onNext = a => addRow(a)),
      div(
        cls := "text-gray-50",
        div(
          cls := "flex flex-row my-1 h-auto",
          columnConfigs.map(cfg =>
            div(
              cls := s"px-2 font-bold text-2xl ${cfg.width}",
              renderHeader(cfg)
            )
          )
        ),
        div(
          cls := "h-auto",
          children <-- rows.signal.map(_.values.toSeq).split(rowKey)(renderRow)
        )
      )
    )

  // Helper method to add a single row to the table
  private def addRow(row: A): Unit =
    rows.update(_.updated(rowKey(row), row))

  // The map of data representing the table entries
  private val rows: Var[Map[K, A]] = Var(Map.empty)

  private def renderRow(key: K, data: A, s: Signal[A]): HtmlElement =
    div(
      cls := "flex flex-row my-3",
      children <-- s.map { a =>
        renderData(a)
      }
    )

  private def renderHeader: WebTable.ColumnConfig[A] => HtmlElement = cfg => span(cfg.header)

  private def renderData: A => Chunk[HtmlElement] = a =>
    columnConfigs.map(cfg =>
      div(
        cls := s"px-2 font-normal text-xl ${cfg.width} grid grid-cols1 content-center",
        cfg.renderer(a)
      )
    )

  // This is a stream of data (case class instances), each data item is identifiable by a key and the table will display
  // a single row for each key
  def data: EventStream[A]

  // The key for a single data row derived from the case class instance
  def rowKey: A => K

  def derived: Map[Double, WebTable.ColumnConfig[A]] = Map.empty

  def extra: Map[Double, WebTable.ColumnConfig[A]] = Map.empty

  // The column configurations for a row
  def columnConfigs: Chunk[WebTable.ColumnConfig[A]]
}

object WebTable {

  def create[K, A](
    // The WebRow derivation for the case class
    cols: Chunk[ColumnConfig[A]],
    // How to retrieve the row key
    rk: A => K
  )(
    s: EventStream[A]
  ): WebTable[K, A] =
    new WebTable[K, A] {
      override def rowKey                                = rk
      override def data: EventStream[A]                  = s
      override def columnConfigs: Chunk[ColumnConfig[A]] = cols
    }

  sealed trait ColumnAlign
  object ColumnAlign {
    case object Left   extends ColumnAlign
    case object Right  extends ColumnAlign
    case object Center extends ColumnAlign
  }

  final case class ColumnConfig[A](
    header: String = "",
    align: ColumnAlign = ColumnAlign.Center,
    width: String = "w-1/6",
    renderer: A => HtmlElement
  )
}
