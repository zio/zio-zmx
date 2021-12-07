package zio.zmx.client

import zio._
import zio.metrics._
import java.time.Instant

import upickle.default._

object UPickleCoreImplicits {
  implicit val rwInstant: ReadWriter[Instant]   =
    readwriter[Long].bimap(_.toEpochMilli(), Instant.ofEpochMilli(_))
  implicit val rwDuration: ReadWriter[Duration] =
    readwriter[Long].bimap(_.toMillis(), Duration.fromMillis(_))

  implicit lazy val rwMetricLabel: ReadWriter[MetricLabel] = macroRW[MetricLabel]

  implicit lazy val rwMetricKey: ReadWriter[MetricKey]              = macroRW[MetricKey]
  implicit lazy val rwGaugeKey: ReadWriter[MetricKey.Gauge]         = macroRW[MetricKey.Gauge]
  implicit lazy val rwHistogramKey: ReadWriter[MetricKey.Histogram] = macroRW[MetricKey.Histogram]
  implicit lazy val rwCounterKey: ReadWriter[MetricKey.Counter]     = macroRW[MetricKey.Counter]
  implicit lazy val rwSummaryKey: ReadWriter[MetricKey.Summary]     = macroRW[MetricKey.Summary]
  implicit lazy val rwSetCountKey: ReadWriter[MetricKey.SetCount]   = macroRW[MetricKey.SetCount]

  implicit lazy val rwMetricTypeCounter   = macroRW[MetricType.Counter]
  implicit lazy val rwMetricTypeGauge     = macroRW[MetricType.Gauge]
  implicit lazy val rwMetricTypeHistogram = macroRW[MetricType.DoubleHistogram]
  implicit lazy val rwMetricTypeSummary   = macroRW[MetricType.Summary]
  implicit lazy val rwMetricTypeSetCount  = macroRW[MetricType.SetCount]

  implicit lazy val rwMetricState: ReadWriter[MetricState]                   = macroRW[MetricState]
  implicit lazy val rwMetricType: ReadWriter[MetricType]                     = macroRW[MetricType]
  implicit lazy val rwBoundaries: ReadWriter[ZIOMetric.Histogram.Boundaries] = macroRW[ZIOMetric.Histogram.Boundaries]
}

sealed trait ClientMessage

object ClientMessage {
  case object Connect                                                           extends ClientMessage
  final case class Connected(cltId: String)                                     extends ClientMessage
  final case class Subscribe(clt: String, id: String, interval: Duration)       extends ClientMessage
  final case class AddMetrics(clt: String, id: String, keys: Seq[MetricKey])    extends ClientMessage
  final case class RemoveMetrics(clt: String, id: String, keys: Seq[MetricKey]) extends ClientMessage
  final case class SetInterval(clt: String, id: String, interval: Duration)     extends ClientMessage
  final case class MetricsNotification(update: Seq[MetricsUpdate])              extends ClientMessage

  import UPickleCoreImplicits._
  import MetricsUpdate._

  implicit lazy val rwClientMessage: ReadWriter[ClientMessage]      = macroRW[ClientMessage]
  implicit lazy val rwConnected: ReadWriter[Connected]              = macroRW[Connected]
  implicit lazy val rwSubscribe: ReadWriter[Subscribe]              = macroRW[Subscribe]
  implicit lazy val rwAddMetrics: ReadWriter[AddMetrics]            = macroRW[AddMetrics]
  implicit lazy val rwRemoveMetrics: ReadWriter[RemoveMetrics]      = macroRW[RemoveMetrics]
  implicit lazy val rwSetInterval: ReadWriter[SetInterval]          = macroRW[SetInterval]
  implicit lazy val rwNotification: ReadWriter[MetricsNotification] = macroRW[MetricsNotification]
}

sealed trait MetricsUpdate {
  def key: MetricKey
  def when: Instant
}

object MetricsUpdate {

  def fromMetricState(k: MetricKey, s: MetricState): MetricsUpdate = ???

  final case class GaugeChange(key: MetricKey.Gauge, when: Instant, value: Double)        extends MetricsUpdate
  final case class CounterChange(key: MetricKey.Counter, when: Instant, absValue: Double) extends MetricsUpdate

  final case class HistogramChange(key: MetricKey.Histogram, when: Instant, value: MetricState) extends MetricsUpdate
  final case class SummaryChange(key: MetricKey.Summary, when: Instant, value: MetricState)     extends MetricsUpdate
  final case class SetChange(key: MetricKey.SetCount, when: Instant, value: MetricState)        extends MetricsUpdate

  import UPickleCoreImplicits._

  implicit lazy val rwGaugeChange: ReadWriter[GaugeChange]         = macroRW[GaugeChange]
  implicit lazy val rwCounterChange: ReadWriter[CounterChange]     = macroRW[CounterChange]
  implicit lazy val rwHistogramChange: ReadWriter[HistogramChange] = macroRW[HistogramChange]
  implicit lazy val rwSummaryChange: ReadWriter[SummaryChange]     = macroRW[SummaryChange]
  implicit lazy val rwSetChange: ReadWriter[SetChange]             = macroRW[SetChange]

  implicit lazy val rwUpdate: ReadWriter[MetricsUpdate] = macroRW[MetricsUpdate]

}
