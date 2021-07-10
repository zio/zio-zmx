package zio.zmx.client.frontend

import zio.Chunk

import org.scalajs.dom
import com.raquo.laminar.api.L._
import zio.zmx.client.frontend.webtable.WebTable
import AppDataModel.MetricSummary._

object AppViews {

  val summaries: HtmlElement = div(
    counterInfoView.render(AppState.counterInfo),
    gaugeInfoView.render(AppState.gaugeInfo),
    histogramInfoView.render(AppState.histogramInfo),
    summaryInfoView.render(AppState.summaryInfo),
    setInfoView.render(AppState.setInfo)
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

  private def diagramLink(k: String, dt: String, f: String => Unit): Chunk[HtmlElement] = {
    val handler = Observer[dom.MouseEvent](onNext = _ => f(k))
    Chunk(a(href("#"), s"Add $dt diagram for $k", onClick --> handler))
  }

  private lazy val counterInfoView: WebTable[String, CounterInfo] =
    WebTable.create[String, CounterInfo](
      wr = WebTable.DeriveRow.gen[CounterInfo],
      rk = _.longName,
      extra = k => diagramLink(k, "counter", AppState.addCounterDiagram)
    )

  private lazy val gaugeInfoView: WebTable[String, GaugeInfo] =
    WebTable.create[String, GaugeInfo](
      wr = WebTable.DeriveRow.gen[GaugeInfo],
      rk = _.longName,
      extra = k => diagramLink(k, "gauge", AppState.addGaugeDiagram)
    )

  private lazy val histogramInfoView: WebTable[String, HistogramInfo] =
    WebTable.create[String, HistogramInfo](
      wr = WebTable.DeriveRow.gen[HistogramInfo],
      rk = _.longName,
      extra = k => diagramLink(k, "histogram", AppState.addHistogramDiagram)
    )

  private lazy val summaryInfoView: WebTable[String, SummaryInfo] =
    WebTable.create[String, SummaryInfo](
      wr = WebTable.DeriveRow.gen[SummaryInfo],
      rk = _.longName,
      extra = k => diagramLink(k, "summary", AppState.addSummaryDiagram)
    )

  private lazy val setInfoView: WebTable[String, SetInfo] =
    WebTable.create[String, SetInfo](
      wr = WebTable.DeriveRow.gen[SetInfo],
      rk = _.longName,
      extra = k => diagramLink(k, "set", AppState.addSetDiagram)
    )
}
