package zio.zmx.client

import zio.metrics._
import java.time.Instant

import upickle.default._
import zio.MetricLabel
import zio.Duration
import zio.ZIOMetric

sealed trait ClientMessage

object ClientMessage {
  def subscribe: ClientMessage = Subscribe

  case object Subscribe extends ClientMessage
}

sealed trait MetricsMessage {
  def key: MetricKey
  def when: Instant
}

object MetricsMessage {
  implicit val rwInstant: ReadWriter[Instant]   =
    readwriter[Long].bimap(_.toEpochMilli(), Instant.ofEpochMilli(_))
  implicit val rwDuration: ReadWriter[Duration] =
    readwriter[Long].bimap(_.toMillis(), Duration.fromMillis(_))

  implicit val rwMetricLabel: ReadWriter[MetricLabel] = macroRW[MetricLabel]

  implicit val rwGaugeKey: ReadWriter[MetricKey.Gauge]         = macroRW[MetricKey.Gauge]
  implicit val rwCounterKey: ReadWriter[MetricKey.Counter]     = macroRW[MetricKey.Counter]
  implicit val rwHistogramKey: ReadWriter[MetricKey.Histogram] = macroRW[MetricKey.Histogram]
  implicit val rwSummaryKey: ReadWriter[MetricKey.Summary]     = macroRW[MetricKey.Summary]
  implicit val rwSetCountKey: ReadWriter[MetricKey.SetCount]   = macroRW[MetricKey.SetCount]

  implicit val rwMetricTypeCounter   = macroRW[MetricType.Counter]
  implicit val rwMetricTypeGauge     = macroRW[MetricType.Gauge]
  implicit val rwMetricTypeHistogram = macroRW[MetricType.DoubleHistogram]
  implicit val rwMetricTypeSummary   = macroRW[MetricType.Summary]
  implicit val rwMetricTypeSetCount  = macroRW[MetricType.SetCount]

  implicit val rwMetricState: ReadWriter[MetricState]                   = macroRW[MetricState]
  implicit val rwMetricType: ReadWriter[MetricType]                     = macroRW[MetricType]
  implicit val rwBoundaries: ReadWriter[ZIOMetric.Histogram.Boundaries] = macroRW[ZIOMetric.Histogram.Boundaries]

  implicit val rw = macroRW[MetricsMessage]

  final case class GaugeChange(key: MetricKey.Gauge, when: Instant, value: Double, delta: Double) extends MetricsMessage
  object GaugeChange   {
    implicit val rw: ReadWriter[GaugeChange] = macroRW[GaugeChange]
  }
  final case class CounterChange(key: MetricKey.Counter, when: Instant, absValue: Double, delta: Double)
      extends MetricsMessage
  object CounterChange {
    implicit val rw: ReadWriter[CounterChange] = macroRW[CounterChange]
  }

  final case class HistogramChange(key: MetricKey.Histogram, when: Instant, value: MetricState) extends MetricsMessage
  object HistogramChange {
    implicit val rw: ReadWriter[HistogramChange] = macroRW[HistogramChange]
  }
  final case class SummaryChange(key: MetricKey.Summary, when: Instant, value: MetricState) extends MetricsMessage
  object SummaryChange   {
    implicit val rw: ReadWriter[SummaryChange] = macroRW[SummaryChange]
  }
  final case class SetChange(key: MetricKey.SetCount, when: Instant, value: MetricState) extends MetricsMessage
  object SetChange       {
    implicit val rw: ReadWriter[SetChange] = macroRW[SetChange]
  }

}
