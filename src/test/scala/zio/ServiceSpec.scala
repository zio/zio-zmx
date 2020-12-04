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

import zio.clock.Clock
import zio.console.Console
import zio.duration._
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestClock
import zio.zmx.Metrics._
import zio.zmx._

object ServiceSpec extends DefaultRunnableSpec {

  val config = MetricsConfig(
    maximumSize = 20,
    bufferSize = 5,
    timeout = 5.seconds,
    pollRate = 100.millis,
    host = None,
    port = None
  )

  def testMetricsServiceByCheckingEmittedChunks[E](label: String)(
    assertion: Queue[Chunk[Byte]] => ZIO[TestClock with Clock with Console with Metrics, E, TestResult]
  ): ZSpec[TestClock with Clock with Console, E] =
    testM(label) {
      for {
        // collects the published chunks of bytes
        queue <- ZQueue.unbounded[Chunk[Byte]]

        senderLayer = ZLayer.succeed(new MetricsSender.Service[Chunk[Byte]] {
                        override def send(b: Chunk[Byte]): UIO[Unit] = queue.offer(b).as(())
                      })

        metricsLayer =
          ZLayer.identity[Clock] ++ senderLayer >>> MetricsAggregator.live(config) >>> Metrics.fromAggregator

        testResult <- assertion(queue)
                        .provideSomeLayer[TestClock with Clock with Console](metricsLayer)
      } yield testResult
    }

  val testSendMetricsLive: RIO[Metrics, TestResult] = for {
    b <- counter("safe-zmx", 2.0, 1.0)
  } yield assert(b)(equalTo(true))

  def testCollectMetricsLive(queue: Queue[Chunk[Byte]]): RIO[TestClock with Metrics, TestResult] =
    for {
      // send one more counter event than the aggregation size
      _              <- ZIO.foreach((1 to config.bufferSize + 1).toList)(i =>
                          counter("test-zmx", i.toDouble, 1.0, Label("test", "zmx"))
                        )
      _              <- TestClock.adjust(config.pollRate)
      emittedMetrics <- queue.takeAll
    } yield assert(emittedMetrics)(
      equalTo(
        // the last counter event is still in the aggregator
        (1 to config.bufferSize)
          .map(i => Metric.Counter("test-zmx", i.toDouble, 1.0, Chunk(Label("test", "zmx"))))
          .map(MetricsAggregator.encodeMetric)
          .toList
      )
    )

  def testSendOnTimeout(queue: Queue[Chunk[Byte]]): RIO[TestClock with Metrics, TestResult] = for {
    _              <- counter("test-zmx", 1.0, 1.0, Label("test", "zmx"))
    _              <- counter("test-zmx", 3.0, 1.0, Label("test", "zmx"))
    _              <- counter("test-zmx", 5.0, 1.0, Label("test", "zmx"))
    _              <- TestClock.adjust(config.timeout)
    emittedMetrics <- queue.takeAll
  } yield assert(emittedMetrics)(
    equalTo(
      List(
        Metric.Counter("test-zmx", 1.0, 1.0, Chunk(Label("test", "zmx"))),
        Metric.Counter("test-zmx", 3.0, 1.0, Chunk(Label("test", "zmx"))),
        Metric.Counter("test-zmx", 5.0, 1.0, Chunk(Label("test", "zmx")))
      ).map(MetricsAggregator.encodeMetric)
    )
  )

  def spec =
    suite("Service Spec")(
      suite("Using the Service directly")(
        testM("does not leak sockets") {
          MetricsSender.udpClientMetricsSender(config).build.use(_ => ZIO.succeed(assertCompletes))
        } @@ TestAspect.nonFlaky(1000),
        testMetricsServiceByCheckingEmittedChunks("send returns true") { _ =>
          testSendMetricsLive
        },
        testMetricsServiceByCheckingEmittedChunks("Send on 5") { queue =>
          testCollectMetricsLive(queue)
        },
        testMetricsServiceByCheckingEmittedChunks("Send 3 on timeout") { queue =>
          testSendOnTimeout(queue)
        }
      )
    )
}
