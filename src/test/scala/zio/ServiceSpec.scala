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
import zio.duration._
import zio.zmx._
import zio.clock.Clock

import zio.zmx.MetricsConfigDataModel._
import zio.zmx.MetricsDataModel._

object ServiceSpec extends DefaultRunnableSpec {

  val config = MetricsConfig(
    maximumSize = 20,
    bufferSize = 5,
    timeout = 5.seconds,
    pollRate = 100.millis,
    host = None,
    port = None
  )

  val testSendMetricsLive: RIO[Metrics, TestResult] = for {
    b <- counter("safe-zmx", 2.0, 1.0)
  } yield assert(b)(equalTo(true))

  val testCollectMetricsLive: RIO[Metrics, TestResult] = for {
    _              <- counter("test-zmx", 1.0, 1.0, Label("test", "zmx"))
    _              <- counter("test-zmx", 3.0, 1.0, Label("test", "zmx"))
    _              <- counter("test-zmx", 1.0, 1.0, Label("test", "zmx"))
    _              <- counter("test-zmx", 5.0, 1.0, Label("test", "zmx"))
    _              <- counter("test-zmx", 4.0, 1.0, Label("test", "zmx"))
    _              <- counter("test-zmx", 2.0, 1.0, Label("test", "zmx"))
    queue          <- ZQueue.unbounded[Chunk[Metric[_]]]
    _              <- listen(metrics => queue.offer(metrics).as(metrics.map(_ => 1L)))
    emittedMetrics <- queue.take
  } yield assert(emittedMetrics)(
    equalTo(
      Chunk(
        Metric.Counter("test-zmx", 1.0, 1.0, Chunk(Label("test", "zmx"))),
        Metric.Counter("test-zmx", 3.0, 1.0, Chunk(Label("test", "zmx"))),
        Metric.Counter("test-zmx", 1.0, 1.0, Chunk(Label("test", "zmx"))),
        Metric.Counter("test-zmx", 5.0, 1.0, Chunk(Label("test", "zmx"))),
        Metric.Counter("test-zmx", 4.0, 1.0, Chunk(Label("test", "zmx")))
      )
    )
  )

  val testSendOnTimeout: RIO[Metrics, TestResult] = for {
    queue          <- ZQueue.unbounded[Chunk[Metric[_]]]
    _              <- listen(metrics => queue.offer(metrics).as(metrics.map(_ => 1L)))
    _              <- counter("test-zmx", 1.0, 1.0, Label("test", "zmx"))
    _              <- counter("test-zmx", 3.0, 1.0, Label("test", "zmx"))
    _              <- counter("test-zmx", 5.0, 1.0, Label("test", "zmx"))
    emittedMetrics <- queue.take
  } yield assert(emittedMetrics)(
    equalTo(
      Chunk(
        Metric.Counter("test-zmx", 1.0, 1.0, Chunk(Label("test", "zmx"))),
        Metric.Counter("test-zmx", 3.0, 1.0, Chunk(Label("test", "zmx"))),
        Metric.Counter("test-zmx", 5.0, 1.0, Chunk(Label("test", "zmx")))
      )
    )
  )

  def spec =
    suite("Service Spec")(
      suite("Using the Service directly")(
        testM("does not leak sockets") {
          Metrics.live(config).build.use(_ => ZIO.succeed(assertCompletes))
        } @@ TestAspect.nonFlaky(1000),
        testM("send returns true") {
          testSendMetricsLive.provideSomeLayer(Metrics.live(config))
        },
        testM("Send on 5") {
          testCollectMetricsLive.provideLayer(Clock.live >>> Metrics.live(config.copy(timeout = 30.seconds)))
        },
        testM("Send 3 on timeout") {
          testSendOnTimeout.provideLayer(Clock.live >>> Metrics.live(config))
        }
      )
    )
}
