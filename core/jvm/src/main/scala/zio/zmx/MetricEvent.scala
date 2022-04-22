package zio.zmx

import java.time.Instant

import zio._
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

  def make[Type <: MetricKeyType { type Out = Out0 }, Out0](
    metricKey: MetricKey[Type],
    oldState: Option[MetricState[Out0]],
    newState: MetricState[Out0],
  ): ZIO[Any, IllegalArgumentException, MetricEvent] =
    (oldState, newState) match {
      case (Some(oldState @ MetricState.Counter(oldCount)), newState @ MetricState.Counter(newCount))               =>
        if (oldCount != newCount)
          ZIO.succeed(Updated(metricKey, oldState, newState, Instant.now))
        else
          ZIO.succeed(Unchanged(MetricPair.unsafeMake(metricKey, newState), Instant.now))
      case (Some(oldState @ MetricState.Gauge(oldValue)), newState @ MetricState.Gauge(newValue))                   =>
        if (oldValue != newValue)
          ZIO.succeed(Updated(metricKey, oldState, newState, Instant.now))
        else
          ZIO.succeed(Unchanged(MetricPair.unsafeMake(metricKey, newState), Instant.now))
      case (Some(oldState @ MetricState.Frequency(oldOccurences)), newState @ MetricState.Frequency(newOcurrences)) =>
        if (oldOccurences != newOcurrences)
          ZIO.succeed(Updated(metricKey, oldState, newState, Instant.now))
        else
          ZIO.succeed(Unchanged(MetricPair.unsafeMake(metricKey, newState), Instant.now))
      case (
            Some(oldState @ MetricState.Summary(_, _, oldCount, _, _, _)),
            newState @ MetricState.Summary(_, _, newCount, _, _, _),
          ) =>
        if (oldCount != newCount)
          ZIO.succeed(Updated(metricKey, oldState, newState, Instant.now))
        else
          ZIO.succeed(Unchanged(MetricPair.unsafeMake(metricKey, newState), Instant.now))
      case (
            Some(oldState @ MetricState.Histogram(_, oldCount, _, _, _)),
            newState @ MetricState.Histogram(_, newCount, _, _, _),
          ) =>
        if (oldCount != newCount)
          ZIO.succeed(Updated(metricKey, oldState, newState, Instant.now))
        else
          ZIO.succeed(Unchanged(MetricPair.unsafeMake(metricKey, newState), Instant.now))
      case (None, state)                                                                                            =>
        ZIO.succeed(New(MetricPair.unsafeMake(metricKey, state), Instant.now))
      case (oldState, newState)                                                                                     =>
        ZIO.fail(new IllegalArgumentException(s"Unsupported MetricState combination: $oldState, $newState"))

    }

}
