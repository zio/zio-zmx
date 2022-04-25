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

import MetricPublisher.Result
import zhttp.service._
trait MetricPublisher[A] {

  def publish(metrics: Iterable[A]): ZIO[Any, Nothing, Result]

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

  val newRelic = ZLayer.fromZIO {
    for {
      channelFactory <- ZIO.service[ChannelFactory]
      eventLoopGroop <- ZIO.service[EventLoopGroup]
      settings       <- ZIO.service[NewRelicPublisher.Settings]

    } yield NewRelicPublisher(channelFactory, eventLoopGroop, settings)
  }

}
