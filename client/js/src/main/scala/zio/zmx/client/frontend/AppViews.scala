package zio.zmx.client.frontend

import zio.Chunk

import org.scalajs.dom
import com.raquo.laminar.api.L._
import zio.zmx.client.frontend.webtable.WebTable
import AppDataModel.MetricSummary._

object AppViews {

  val summaries: HtmlElement = div(
    counterInfoView.render
    // gaugeInfoView.render,
    // histogramInfoView.render,
    // summaryInfoView.render,
    // setInfoView.render
  )

  val diagrams: HtmlElement =
    div(
      div(
        cls := "bg-gray-900 text-gray-50 rounded p-3 my-3",
        span(
          cls := "text-3xl font-bold my-2",
          "Diagrams"
        )
      ),
      children <-- AppState.diagrams.signal.map(c => c.map(_.render()))
    )

  private def diagramLink(k: String, f: String => Unit): HtmlElement = {
    val handler = Observer[dom.MouseEvent](onNext = _ => f(k))
    a(
      href("#"),
      s"Add diagram",
      cls := "bg-blue-500 hover:bg-blue-700 text-white font-bold py-1 px-4 rounded text-center place-self-center",
      onClick --> handler
    )
  }

  private lazy val counterInfoView: WebTable[String, CounterInfo] =
    WebTable.create[String, CounterInfo](
      cols = Chunk(
        WebTable.ColumnConfig[CounterInfo](
          width = "w-60",
          renderer = ci => diagramLink(ci.longName, AppState.addCounterDiagram)
        ),
        WebTable.ColumnConfig[CounterInfo](
          "Name",
          renderer = ci => span(ci.name)
        ),
        WebTable.ColumnConfig[CounterInfo](
          "Labels",
          renderer = ci => span(ci.labels)
        ),
        WebTable.ColumnConfig[CounterInfo](
          "Current",
          renderer = ci => span(f"${ci.current.longValue()}%,d")
        )
      ),
      rk = _.longName
    )(AppState.counterInfo)

  // private lazy val gaugeInfoView: WebTable[String, GaugeInfo] =
  //   WebTable.create[String, GaugeInfo](
  //     wr = WebTable.DeriveRow.gen[GaugeInfo],
  //     rk = _.longName,
  //     extra = k => diagramLink(k, "gauge", AppState.addGaugeDiagram)
  //   )(AppState.gaugeInfo)

  // private lazy val histogramInfoView: WebTable[String, HistogramInfo] =
  //   WebTable.create[String, HistogramInfo](
  //     wr = WebTable.DeriveRow.gen[HistogramInfo],
  //     rk = _.longName,
  //     extra = k => diagramLink(k, "histogram", AppState.addHistogramDiagram)
  //   )(AppState.histogramInfo)

  // private lazy val summaryInfoView: WebTable[String, SummaryInfo] =
  //   WebTable.create[String, SummaryInfo](
  //     wr = WebTable.DeriveRow.gen[SummaryInfo],
  //     rk = _.longName,
  //     extra = k => diagramLink(k, "summary", AppState.addSummaryDiagram)
  //   )(AppState.summaryInfo)

  // private lazy val setInfoView: WebTable[String, SetInfo] =
  //   WebTable.create[String, SetInfo](
  //     wr = WebTable.DeriveRow.gen[SetInfo],
  //     rk = _.longName,
  //     extra = k => diagramLink(k, "set", AppState.addSetDiagram)
  //   )(AppState.setInfo)
}
