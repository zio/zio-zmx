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
import zio.console._
import zio.duration._
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestClock
import zio.zmx._

object MetricServiceSpec extends DefaultRunnableSpec {

  // tests the ring buffer aggregator

  val config = MetricsConfig(maximumSize = 20, bufferSize = 5, timeout = 5.seconds, pollRate = 100.millis, None, None)

  def testMetricsServiceByCounting[E](label: String)(
    assertion: Ref[Int] => ZIO[TestClock with Clock with Console with Metrics, E, TestResult]
  ): ZSpec[TestClock with Clock with Console, E] =
    testM(label) {
      for {
        // counts the number of sent metrics
        ref                             <- ZRef.make(0)
        sender                           = ZLayer.succeed(new MetricsSender.Service[Chunk[Byte]] {
                                             override def send(b: Chunk[Byte]): UIO[Unit] = ref.update(_ + 1)
                                           })
        metrics: URLayer[Clock, Metrics] =
          ZLayer.identity[Clock] ++ sender >>> MetricsAggregator.live(config) >>> Metrics.fromAggregator

        testResult <- assertion(ref)
                        .provideSomeLayer[TestClock with Clock with Console](metrics)
      } yield testResult
    }

  val neverCompletingSender: ULayer[MetricsSender[Chunk[Byte]]] =
    ZLayer.succeed(new MetricsSender.Service[Chunk[Byte]] {
      override def send(b: Chunk[Byte]): UIO[Unit] = ZIO.never
    })

  // the ring buffer aggregator uses a single fiber for sending aggregated metrics
  // -> if sending never completes then ring buffer will eventually be filled up
  val starvingSenderMetrics: URLayer[Clock, Metrics] =
    ZLayer.identity[Clock] ++ neverCompletingSender >>> MetricsAggregator.live(config) >>> Metrics.fromAggregator

  def spec =
    suite("MetricService Spec")(
      testMetricsServiceByCounting("Send exactly #bufferSize metrics") { ref =>
        for {
          metrics <- ZIO.service[Metrics.Service]
          // completely fill the aggregation buffer
          _       <- ZIO.foreachPar_((1 to config.bufferSize).toSet)(_ =>
                       metrics.counter("test-zmx", 1.0, 1.0, Label("test", "zmx"))
                     )
          // wait some time (shorter than config.timeout)
          // -> metrics get published because the buffer is full
          _       <- TestClock.adjust(config.timeout.dividedBy(2))
          count   <- ref.get
        } yield assert(count)(equalTo(config.bufferSize))
      },
      testMetricsServiceByCounting("Send (#bufferSize - 1) metrics") { ref =>
        for {
          metrics <- ZIO.service[Metrics.Service]
          // completely fill the aggregation buffer except one element
          _       <- ZIO.foreachPar_((1 until config.bufferSize).toSet)(_ =>
                       metrics.counter("test-zmx", 1.0, 1.0, Label("test", "zmx"))
                     )
          // wait some time (shorter than config.timeout)
          // -> metrics get not published because the buffer is not yet full
          _       <- TestClock.adjust(config.timeout.dividedBy(2))
          count1  <- ref.get
          // wait some more time
          _       <- TestClock.adjust(config.timeout)
          // currently buffered metrics are published by now
          count2  <- ref.get
        } yield assert(count1)(equalTo(0)) && assert(count2)(equalTo(config.bufferSize - 1))
      },
      testM("Send eventually fails without poll") {
        (for {
          metrics <- ZIO.service[Metrics.Service]

          // listen starts with a poll on a forked fiber so it could remove some of the metrics sent below before
          // it gets blocked by the test clock.
          // as the poll result is not exposed we can't explicitly wait for it so instead
          // we assume that this may happen and send metrics until the buffer gets full

          last <- metrics.counter("test-zmx", 1.0, 1.0, Label("test", "zmx")).repeatWhileEquals(true)
        } yield assert(last)(equalTo(false))).provideSomeLayer(starvingSenderMetrics)
      } @@ TestAspect.nonFlaky(1000)
    )
}
