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

import zhttp.service._

package object newrelic {

  val quickStart = {
    val publisherSettings = ZLayer.fromZIO(
      envVar("NEW_RELIC_API_KEY", "New Relic ZMX Publisher")
        .map(NewRelicPublisher.Settings.forNA),
    )

    val zhttp = EventLoopGroup.nio() ++ ChannelFactory.nio

    val newRelic = MetricEventEncoder.newRelic ++ MetricPublisher.newRelic

    (publisherSettings >+> zhttp >+> newRelic) >+> MetricListener.newRelic
  }
}
