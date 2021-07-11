package zio.zmx.client.frontend

import zio.Chunk

import org.scalajs.dom
import com.raquo.laminar.api.L._
import zio.zmx.client.frontend.webtable.WebTable
import AppDataModel.MetricSummary._
import zio.zmx.client.frontend.views.ChartView

object AppViews {

  val summaries: HtmlElement = div(
    counterInfoView.render,
    gaugeInfoView.render,
    histogramInfoView.render,
    summaryInfoView.render,
    setInfoView.render
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
      ChartView.create(),
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
    )(AppState.counterInfo)

  private lazy val gaugeInfoView: WebTable[String, GaugeInfo] =
    WebTable.create[String, GaugeInfo](
      wr = WebTable.DeriveRow.gen[GaugeInfo],
      rk = _.longName,
      extra = k => diagramLink(k, "gauge", AppState.addGaugeDiagram)
    )(AppState.gaugeInfo)

  private lazy val histogramInfoView: WebTable[String, HistogramInfo] =
    WebTable.create[String, HistogramInfo](
      wr = WebTable.DeriveRow.gen[HistogramInfo],
      rk = _.longName,
      extra = k => diagramLink(k, "histogram", AppState.addHistogramDiagram)
    )(AppState.histogramInfo)

  private lazy val summaryInfoView: WebTable[String, SummaryInfo] =
    WebTable.create[String, SummaryInfo](
      wr = WebTable.DeriveRow.gen[SummaryInfo],
      rk = _.longName,
      extra = k => diagramLink(k, "summary", AppState.addSummaryDiagram)
    )(AppState.summaryInfo)

  private lazy val setInfoView: WebTable[String, SetInfo] =
    WebTable.create[String, SetInfo](
      wr = WebTable.DeriveRow.gen[SetInfo],
      rk = _.longName,
      extra = k => diagramLink(k, "set", AppState.addSetDiagram)
    )(AppState.setInfo)
}
