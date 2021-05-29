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

// TODO: Review once first cut of aligned API is ready
// import zio.clock.Clock
// import zio.console._
// import zio.duration._
// import zio.test.Assertion._
// import zio.test._
// import zio.test.environment.TestClock
// import zio.zmx._
//
// import zio.zmx.MetricsDataModel._
// import zio.zmx.MetricsConfigDataModel._
// import zio.zmx.statsd.StatsdClient
//
object StatsdServiceSpec { // extends DefaultRunnableSpec {
//
//   val config = MetricsConfig(maximumSize = 20, bufferSize = 5, timeout = 5.seconds, pollRate = 100.millis, None, None)
//
//   def testMetricsService[E](label: String)(
//     assertion: => ZIO[TestClock with Clock with Metrics with Console, E, TestResult]
//   ): ZSpec[TestClock with Clock with Console, E] =
//     testM(label)(
//       assertion.provideSomeLayer[TestClock with Clock with Console](
//         TestClock.default ++ StatsdClient.live(config).orDie
//       )
//     )
//
//   def spec =
//     suite("MetricService Spec")(
//       testMetricsService("Send exactly #bufferSize metrics") {
//         for {
//           // counts the number of published metrics
//           ref     <- ZRef.make(0)
//           metrics <- ZIO.service[Metrics.Service]
//           _       <- metrics.listen(list => ref.update(_ + list.size).as(Chunk.empty))
//           // completely fill the aggregation buffer
//           _       <- ZIO.foreachPar_((1 to config.bufferSize).toSet)(_ =>
//                        metrics.counter("test-zmx", 1.0, 1.0, Label("test", "zmx"))
//                      )
//           // wait some time (shorter than config.timeout)
//           // -> metrics get published because the buffer is full
//           _       <- TestClock.adjust(config.timeout.dividedBy(2))
//           count   <- ref.get
//         } yield assert(count)(equalTo(config.bufferSize))
//       },
//       testMetricsService("Send (#bufferSize - 1) metrics") {
//         for {
//           // count the number of published metrics
//           ref     <- ZRef.make(0)
//           metrics <- ZIO.service[Metrics.Service]
//           _       <- metrics.listen(list => ref.update(_ + list.size).as(Chunk.empty))
//           // completely fill the aggregation buffer except one element
//           _       <- ZIO.foreachPar_((1 until config.bufferSize).toSet)(_ =>
//                        metrics.counter("test-zmx", 1.0, 1.0, Label("test", "zmx"))
//                      )
//           // wait some time (shorter than config.timeout)
//           // -> metrics get not published because the buffer is not yet full
//           _       <- TestClock.adjust(config.timeout.dividedBy(2))
//           count1  <- ref.get
//           // wait some more time
//           _       <- TestClock.adjust(config.timeout)
//           // currently buffered metrics are published by now
//           count2  <- ref.get
//         } yield assert(count1)(equalTo(0)) && assert(count2)(equalTo(config.bufferSize - 1))
//       },
//       testMetricsService("Send eventually fails without poll") {
//         for {
//           // count the number of published metrics
//           ref     <- ZRef.make(0)
//           metrics <- ZIO.service[Metrics.Service]
//
//           _ <- metrics.listen(list => ref.update(_ + list.size).as(Chunk.empty))
//
//           // listen starts with a poll on a forked fiber so it could remove some of the metrics sent below before
//           // it gets blocked by the test clock.
//           // as the poll result is not exposed we can't explicitly wait for it so instead
//           // we assume that this may happen and send metrics until the buffer gets full
//
//           last <- metrics.counter("test-zmx", 1.0, 1.0, Label("test", "zmx")).repeatWhileEquals(true)
//         } yield assert(last)(equalTo(false))
//       } @@ TestAspect.nonFlaky(1000)
//     )
}
