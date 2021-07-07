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

  val diagrams: HtmlElement = div(
    h1("Diagrams"),
    children <-- AppState.diagrams.signal.map(c => c.map(_.render()))
  )

  private lazy val counterInfoView: WebTable[String, CounterInfo] =
    WebTable.create[String, CounterInfo](
      wr = WebTable.DeriveRow.gen[CounterInfo],
      rk = _.longName,
      extra = k => {
        val handler = Observer[dom.MouseEvent](onNext = _ => AppState.addCounterDiagram(k))
        Chunk(a(href("#"), s"Add counter diagram for $k", onClick --> handler))
      }
    )

  private lazy val gaugeInfoView: WebTable[String, GaugeInfo] =
    WebTable.create[String, GaugeInfo](
      wr = WebTable.DeriveRow.gen[GaugeInfo],
      rk = _.longName,
      extra = k => Chunk(a(href("#"), s"Add gauge diagram for $k"))
    )

  private lazy val histogramInfoView: WebTable[String, HistogramInfo] =
    WebTable.create[String, HistogramInfo](
      wr = WebTable.DeriveRow.gen[HistogramInfo],
      rk = _.longName,
      extra = k => Chunk(a(href("#"), s"Add histogram diagram for $k"))
    )

  private lazy val summaryInfoView: WebTable[String, SummaryInfo] =
    WebTable.create[String, SummaryInfo](
      wr = WebTable.DeriveRow.gen[SummaryInfo],
      rk = _.longName,
      extra = k => Chunk(a(href("#"), s"Add summary diagram for $k"))
    )

  private lazy val setInfoView: WebTable[String, SetInfo] =
    WebTable.create[String, SetInfo](
      wr = WebTable.DeriveRow.gen[SetInfo],
      rk = _.longName,
      extra = k => Chunk(a(href("#"), s"Add Set diagram for $k"))
    )
}
