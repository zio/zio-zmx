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
package zio.metrics.connectors

import zio._
import zio.metrics.connectors.newrelic._

trait MetricListener[A] {

  def encoder: MetricEncoder[A]
  def publisher: MetricPublisher[A]

  def update(events: Set[MetricEvent]): UIO[Unit] =
    for {
      // First we initialize the publisher to receive a new snapshot
      // that gives us the chance to collect all metrics that belong to
      // the same snapshot
      _  <- publisher.startSnapshot
      ts <- ZIO.clock.flatMap(_.instant)
      // _ <- Console.printLine(s"Sending events ${events}").orDie
      _  <- ZIO.foreach(events)(e =>
              encoder.encode(e).catchAll(_ => ZIO.succeed(Chunk.empty)).flatMap(c => publisher.publish(c)),
            ) // TODO: log some kind of warning ?
      _  <- publisher.completeSnapshot
    } yield ()
}

object MetricListener {

  val newRelic = ZLayer.fromZIO(for {
    nrEncoder   <- ZIO.service[NewRelicEncoder]
    nrPublisher <- ZIO.service[NewRelicPublisher]

  } yield NewRelicListener(nrEncoder, nrPublisher))
}
