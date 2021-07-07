package zio.zmx.client.frontend

import com.raquo.laminar.api.L._
import zio.zmx.internal.MetricKey
import AppDataModel._

import zio.Chunk
import zio.zmx.client.MetricsMessage
import zio.zmx.client.frontend.AppDataModel.MetricSummary._

object AppState {

  private val summaries: Var[Map[MetricKey, MetricSummary]] = Var(Map.empty)

  def updateSummary(msg: MetricsMessage) =
    MetricSummary.fromMessage(msg).foreach(summary => summaries.update(_.updated(msg.key, summary)))

  val counterInfo: Signal[Chunk[CounterInfo]]     =
    summaries.signal.map(_.collect { case (_, ci: CounterInfo) => ci }).map(Chunk.fromIterable)

  val gaugeInfo: Signal[Chunk[GaugeInfo]]         =
    summaries.signal.map(_.collect { case (_, ci: GaugeInfo) => ci }).map(Chunk.fromIterable)

  val histogramInfo: Signal[Chunk[HistogramInfo]] =
    summaries.signal.map(_.collect { case (_, ci: HistogramInfo) => ci }).map(Chunk.fromIterable)

  val summaryInfo: Signal[Chunk[SummaryInfo]]     =
    summaries.signal.map(_.collect { case (_, ci: SummaryInfo) => ci }).map(Chunk.fromIterable)

  val setInfo: Signal[Chunk[SetInfo]]             =
    summaries.signal.map(_.collect { case (_, ci: SetInfo) => ci }).map(Chunk.fromIterable)
}
