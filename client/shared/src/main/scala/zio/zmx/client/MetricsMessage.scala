package zio.zmx.client

import boopickle.Default._
import zio.zmx.internal.MetricKey
import zio.zmx.state.MetricState

import java.time.Duration
import java.time.Instant

object CustomPicklers {
  implicit val durationPickler: Pickler[Duration] =
    transformPickler((long: Long) => Duration.ofMillis(long))(_.toMillis)

  implicit val instantPickler: Pickler[Instant] =
    transformPickler((long: Long) => Instant.ofEpochMilli(long))(_.toEpochMilli())
}

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
  final case class GaugeChange(key: MetricKey.Gauge, when: Instant, value: Double, delta: Double) extends MetricsMessage
  final case class CounterChange(key: MetricKey.Counter, when: Instant, absValue: Double, delta: Double)         extends MetricsMessage
  final case class HistogramChange(key: MetricKey.Histogram, when: Instant, value: MetricState)                  extends MetricsMessage
  final case class SummaryChange(key: MetricKey.Summary, when: Instant, value: MetricState)                      extends MetricsMessage
  final case class SetChange(key: MetricKey.SetCount, when: Instant, value: MetricState)                         extends MetricsMessage
}
