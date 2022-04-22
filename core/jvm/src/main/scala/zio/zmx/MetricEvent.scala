package zio.zmx

import java.time.Instant

import zio.metrics._

sealed trait MetricEvent

object MetricEvent {

  final case class New(metric: MetricPair.Untyped, timestamp: Instant) extends MetricEvent

  final case class Unchanged(metricPair: MetricPair.Untyped, timestamp: Instant) extends MetricEvent

  final case class Updated(
    metricKey: MetricKey.Untyped,
    oldState: MetricState.Untyped,
    newState: MetricState.Untyped,
    timestamp: Instant)
      extends MetricEvent
}
