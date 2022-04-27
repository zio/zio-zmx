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

import zio._
import zio.zmx.newrelic.NewRelicPublisher

import zhttp.service._
trait MetricPublisher[A] {

  /**
   * Start publishing a new complete Snapshot
   */
  def startSnapshot(implicit trace: ZTraceElement): UIO[Unit] = ZIO.unit

  /**
   * Finish publishing a new complete Snapshot
   */
  def completeSnapshot(implicit trace: ZTraceElement): UIO[Unit] = ZIO.unit

  /**
   * Called by the MetricListener to publish the events associated with a single metric
   */
  def publish(metrics: Iterable[A]): ZIO[Any, Nothing, MetricPublisher.Result]
}

object MetricPublisher {

  sealed trait Result extends Any with Product with Serializable

  object Result {

    case object Success extends Result

    case class TerminalFailure(e: Throwable) extends Result

    case class TransientFailure(e: Throwable) extends Result
  }

  def publish[A: Tag](metrics: Iterable[A]) =
    ZIO.serviceWithZIO[MetricPublisher[A]](_.publish(metrics))

  // TODO: This should not live in the MetricPublisher, but in a backend specific class
  val newRelic = ZLayer.fromZIO {
    for {
      channelFactory <- ZIO.service[ChannelFactory]
      eventLoopGroup <- ZIO.service[EventLoopGroup]
      settings       <- ZIO.service[NewRelicPublisher.Settings]

    } yield NewRelicPublisher(channelFactory, eventLoopGroup, settings)
  }

}
