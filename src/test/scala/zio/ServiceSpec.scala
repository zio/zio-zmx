/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

package zio

import zio.test.Assertion._
import zio.test._
import zio.zmx.Metrics._
import zio.test.environment.TestClock
import zio.duration._
import zio.zmx._
import zio.clock.Clock

object ServiceSpec extends DefaultRunnableSpec {

  val config = new MetricsConfig(20, 5, 5.seconds, None, None)

  val testSendMetricsLive: RIO[Metrics, TestResult] = for {
    b <- counter("safe-zmx", 2.0, 1.0)
  } yield assert(b)(equalTo(true))

  val testCollectMetricsLive: RIO[Metrics with Clock, TestResult] = for {
    _ <- counter("test-zmx", 1.0, 1.0, Label("test", "zmx"))
    _ <- counter("test-zmx", 3.0, 1.0, Label("test", "zmx"))
    _ <- counter("test-zmx", 1.0, 1.0, Label("test", "zmx"))
    _ <- counter("test-zmx", 5.0, 1.0, Label("test", "zmx"))
    _ <- counter("test-zmx", 4.0, 1.0, Label("test", "zmx"))
    _ <- counter("test-zmx", 2.0, 1.0, Label("test", "zmx"))
    _ <- listen()
  } yield assertCompletes

  val testSendOnTimeout = for {

    _ <- listen()
    _ <- counter("test-zmx", 1.0, 1.0, Label("test", "zmx"))
    _ <- counter("test-zmx", 3.0, 1.0, Label("test", "zmx"))
    _ <- counter("test-zmx", 5.0, 1.0, Label("test", "zmx"))
    _ <- TestClock.adjust(5.seconds)
  } yield assertCompletes

  val MetricClock = Metrics.live(config) ++ TestClock.default

  def spec =
    suite("Service Spec")(
      suite("Using the Service directly")(
        zio.test.testM("send returns true") {
          testSendMetricsLive.provideSomeLayer(Metrics.live(config))
        },
        testM("Send on 5") {
          testCollectMetricsLive.provideSomeLayer(MetricClock)
        },
        testM("Send 3 on timeout") {
          testSendOnTimeout.provideSomeLayer(MetricClock)
        }
      )
    )
}
