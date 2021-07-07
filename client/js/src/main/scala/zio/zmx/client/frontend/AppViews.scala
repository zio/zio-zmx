package zio.zmx.client.frontend

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

  private lazy val counterInfoView: WebTable[String, CounterInfo] = {
    val wr = WebTable.DeriveRow.gen[CounterInfo]
    new WebTable[String, CounterInfo] {
      def rowKey: CounterInfo => String        = _.longName
      def webrow: WebTable.WebRow[CounterInfo] = wr
    }
  }

  private lazy val gaugeInfoView: WebTable[String, GaugeInfo] = {
    val wr = WebTable.DeriveRow.gen[GaugeInfo]
    new WebTable[String, GaugeInfo] {
      def rowKey: GaugeInfo => String        = _.longName
      def webrow: WebTable.WebRow[GaugeInfo] = wr
    }
  }

  private lazy val histogramInfoView: WebTable[String, HistogramInfo] = {
    val wr = WebTable.DeriveRow.gen[HistogramInfo]
    new WebTable[String, HistogramInfo] {
      def rowKey: HistogramInfo => String        = _.longName
      def webrow: WebTable.WebRow[HistogramInfo] = wr
    }
  }

  private lazy val summaryInfoView: WebTable[String, SummaryInfo] = {
    val wr = WebTable.DeriveRow.gen[SummaryInfo]
    new WebTable[String, SummaryInfo] {
      def rowKey: SummaryInfo => String        = _.longName
      def webrow: WebTable.WebRow[SummaryInfo] = wr
    }
  }

  private lazy val setInfoView: WebTable[String, SetInfo] = {
    val wr = WebTable.DeriveRow.gen[SetInfo]
    new WebTable[String, SetInfo] {
      def rowKey: SetInfo => String        = _.longName
      def webrow: WebTable.WebRow[SetInfo] = wr
    }
  }
}
