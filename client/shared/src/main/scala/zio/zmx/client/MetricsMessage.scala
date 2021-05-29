package zio.zmx.client

import zio.zmx.internal.MetricKey
import zio.zmx.state.MetricState

sealed trait MetricsMessage

object MetricsMessage {
  final case class GaugeChange(key: MetricKey.Gauge, value: Double, delta: Double)        extends MetricsMessage
  final case class CounterChange(key: MetricKey.Counter, absValue: Double, delta: Double) extends MetricsMessage
  final case class HistogramChange(key: MetricKey.Histogram, value: MetricState)          extends MetricsMessage
  final case class SummaryChange(key: MetricKey.Summary, value: MetricState)              extends MetricsMessage
  final case class SetChange(key: MetricKey.SetCount, value: MetricState)                 extends MetricsMessage
}
