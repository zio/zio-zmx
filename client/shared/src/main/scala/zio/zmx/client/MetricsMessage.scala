package zio.zmx.client

import boopickle.Default._
import zio.zmx.internal.MetricKey
import zio.zmx.state.MetricState

import java.time.Duration

object CustomPicklers {
  implicit val durationPickler: Pickler[Duration] =
    transformPickler((long: Long) => Duration.ofMillis(long))(_.toMillis)
}

sealed trait ClientMessage

object ClientMessage {
  def subscribe: ClientMessage = Subscribe

  case object Subscribe extends ClientMessage
}

sealed trait MetricsMessage {
  def key: MetricKey
}

object MetricsMessage {
  final case class GaugeChange(key: MetricKey.Gauge, value: Double, delta: Double)        extends MetricsMessage
  final case class CounterChange(key: MetricKey.Counter, absValue: Double, delta: Double) extends MetricsMessage
  final case class HistogramChange(key: MetricKey.Histogram, value: MetricState)          extends MetricsMessage
  final case class SummaryChange(key: MetricKey.Summary, value: MetricState)              extends MetricsMessage
  final case class SetChange(key: MetricKey.SetCount, value: MetricState)                 extends MetricsMessage
}
