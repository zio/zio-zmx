package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._
import zio._

import zio.zmx.client.frontend.utils.Modifiers._

trait WebTable[K, A] {

  def rows: StrictSignal[Map[K, A]]

  // The top level render method to include the entire table in the page
  def render: HtmlElement =
    div(
      displayWhen(rows.map(!_.isEmpty)),
      cls := "rounded p-3 my-3 bg-gray-900",
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
          children <-- rows.signal.map(_.toSeq).split(_._1)(renderRow)
        )
      )
    )

  private def renderRow(key: K, data: (K, A), s: Signal[(K, A)]): HtmlElement =
    div(
      cls := "flex flex-row my-3",
      children <-- s.map { row =>
        renderData(row._2)
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

  // The column configurations for a row
  def columnConfigs: Chunk[WebTable.ColumnConfig[A]]
}

object WebTable {

  def create[K, A](
    // The WebRow derivation for the case class
    cols: Chunk[ColumnConfig[A]],
    // How to retrieve the row key
    data: StrictSignal[Map[K, A]]
  ): WebTable[K, A] =
    new WebTable[K, A] {
      override def rows: StrictSignal[Map[K, A]]         = data
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
