/*
 * Copyright 2022 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
 package zio.zmx

import java.time.Instant
import zio.metrics._

sealed trait MetricEvent

object MetricEvent {

  final case class New private (metric: MetricPair.Untyped, timestamp: Instant) extends MetricEvent

  final case class Unchanged private (metricPair: MetricPair.Untyped, timestamp: Instant) extends MetricEvent

  final case class Updated private (
    metricKey: MetricKey.Untyped,
    oldState: MetricState.Untyped,
    newState: MetricState.Untyped,
    timestamp: Instant)
      extends MetricEvent

  def make[Type <: MetricKeyType { type Out = Out0 }, Out0](
    metricKey: MetricKey[Type],
    oldState: Option[MetricState[Out0]],
    newState: MetricState[Out0],
  ): Either[IllegalArgumentException, MetricEvent] =
    (oldState, newState) match {
      case (Some(oldState @ MetricState.Counter(oldCount)), newState @ MetricState.Counter(newCount)) =>
        if (oldCount != newCount)
          Right(Updated(metricKey, oldState, newState, Instant.now))
        else
          Right(Unchanged(MetricPair.unsafeMake(metricKey, newState), Instant.now))

      case (Some(oldState @ MetricState.Gauge(oldValue)), newState @ MetricState.Gauge(newValue)) =>
        if (oldValue != newValue)
          Right(Updated(metricKey, oldState, newState, Instant.now))
        else
          Right(Unchanged(MetricPair.unsafeMake(metricKey, newState), Instant.now))

      case (Some(oldState @ MetricState.Frequency(oldOccurences)), newState @ MetricState.Frequency(newOcurrences)) =>
        if (oldOccurences != newOcurrences)
          Right(Updated(metricKey, oldState, newState, Instant.now))
        else
          Right(Unchanged(MetricPair.unsafeMake(metricKey, newState), Instant.now))

      case (
            Some(oldState @ MetricState.Summary(_, _, oldCount, _, _, _)),
            newState @ MetricState.Summary(_, _, newCount, _, _, _),
          ) =>
        if (oldCount != newCount)
          Right(Updated(metricKey, oldState, newState, Instant.now))
        else
          Right(Unchanged(MetricPair.unsafeMake(metricKey, newState), Instant.now))

      case (
            Some(oldState @ MetricState.Histogram(_, oldCount, _, _, _)),
            newState @ MetricState.Histogram(_, newCount, _, _, _),
          ) =>
        if (oldCount != newCount)
          Right(Updated(metricKey, oldState, newState, Instant.now))
        else
          Right(Unchanged(MetricPair.unsafeMake(metricKey, newState), Instant.now))

      case (None, state) =>
        Right(New(MetricPair.unsafeMake(metricKey, state), Instant.now))

      case (oldState, newState) =>
        Left(new IllegalArgumentException(s"Unsupported MetricState combination: $oldState, $newState"))

    }

}
