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

package zio.zmx.statsd

// TODO: Review once first cut of aligned API is ready
// import zio._
// import zio.clock._
// import zio.console._
// import zio.duration._
//
// import zio.zmx.metrics.ZMetrics
// import zio.zmx.MetricsConfigDataModel._
// import zio.zmx.MetricsDataModel._
// import zio.zmx.statsd.StatsdClient
//
object StatsdServiceApp { //extends App {
//
//   val config = MetricsConfig(maximumSize = 20, bufferSize = 5, timeout = 5.seconds, pollRate = 100.millis, None, None)
//
//   def run(args: List[String]) = app.provideCustomLayer(StatsdClient.live(config)).exitCode
//
//   val app: RIO[Console with Clock with ZMetrics, Unit] = for {
//     metrics <- ZIO.access[ZMetrics](_.get)
// //    fiber   <- metrics.listen()
//     _       <- metrics.counter("test-zmx", 1.0, 1.0, Label("test", "zmx"))
//     _       <- metrics.counter("test-zmx", 3.0, 1.0, Label("test", "zmx"))
//     _       <- metrics.counter("test-zmx", 1.0, 1.0, Label("test", "zmx"))
//     _       <- metrics.counter("test-zmx", 5.0, 1.0, Label("test", "zmx"))
//     _       <- metrics.counter("test-zmx", 4.0, 1.0, Label("test", "zmx"))
//     _       <- metrics.counter("test-zmx", 2.0, 1.0, Label("test", "zmx"))
//     b       <- metrics.counter("test-zmx", 2.0, 1.0, Label("test", "zmx"))
//     _       <- putStrLn(s"send 7th item: $b")
//     _       <- sleep(15.seconds)
//     //   _       <- fiber.interrupt
//   } yield ()
//
}
