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

// TODO: Replace with refactored spec not using prometheus java client lib
// import zio.zmx.Metrics._
// import zio.duration._
// import zio.zmx._
// import zio.clock.Clock
// import zio.console._
//
// import zio.zmx.MetricsConfigDataModel._
// import zio.zmx.MetricsDataModel._
//
// import io.prometheus.client.CollectorRegistry
// import io.prometheus.client.{ Counter => PCounter, Histogram => PHistogram }
// import io.prometheus.client.exporter.common.TextFormat
// import io.prometheus.client.exporter.HTTPServer
//
// import java.io.StringWriter
// import java.io.InvalidObjectException
// import java.net.InetSocketAddress
//
object PrometheusSpec {
//
//   val config = new MetricsConfig(
//     maximumSize = 20,
//     bufferSize = 5,
//     timeout = 5.seconds,
//     pollRate = 100.millis,
//     host = None,
//     port = None
//   )
//
//   val someExternalRegistry = CollectorRegistry.defaultRegistry
//   val c                    = PCounter
//     .build()
//     .name("PrometheusCounter")
//     .labelNames(Array("class", "method"): _*)
//     .help(s"Sample prometheus counter")
//     .register(someExternalRegistry)
//
//   val h = PHistogram
//     .build()
//     .name("PrometheusHistogram")
//     .labelNames(Array("class", "method"): _*)
//     .help(s"Sample prometheus histogram")
//     .register(someExternalRegistry)
//
//   def write004(r: CollectorRegistry): Task[String] =
//     Task {
//       val writer = new StringWriter
//       TextFormat.write004(writer, r.metricFamilySamples)
//       writer.toString
//     }
//
//   def http(r: CollectorRegistry, port: Int): zio.Task[HTTPServer] =
//     Task {
//       new HTTPServer(new InetSocketAddress(port), r)
//     }
//
//   val matchMetric: Metric[_] => IO[Exception, Long] = m => {
//     val e    = new InvalidObjectException("Unknown Metric! Should not happen")
//     val lngs = m match {
//       case Metric.Counter(n, v, _, ts)   =>
//         IO {
//           val tags = n +: (ts.map(_.value).toArray)
//           c.labels(tags: _*).inc(v)
//           v.toLong
//         }
//       case Metric.Histogram(n, v, _, ts) =>
//         IO {
//           val tags = n +: (ts.map(_.value).toArray)
//           h.labels(tags: _*).observe(v)
//           v.toLong
//         }
//       case _                             => IO.fail(e)
//     }
//     lngs.orElseFail(e)
//   }
//
//   val instrument: Chunk[Metric[_]] => IO[Exception, Chunk[Long]] =
//     metrics => {
//       for {
//         longs <- IO.foreach(metrics)(matchMetric)
//       } yield longs
//     }
//
//   val sendOnTimeout: RIO[Metrics with Clock, String] = for {
//     _    <- counter("test-zmx", 1.0, 1.0, Label("test", "zmx"))
//     _    <- counter("test-zmx", 3.0, 1.0, Label("test", "zmx"))
//     _    <- counter("test-zmx", 5.0, 1.0, Label("test", "zmx"))
//     _    <- counter("test-zmx", 2.0, 1.0, Label("test", "zmx"))
//     _    <- counter("test-zmx", 3.0, 1.0, Label("test", "zmx"))
//     _    <- counter("test-zmx", 4.0, 1.0, Label("test", "zmx"))
//     _    <- histogram("test-zmx", 3.0, 1.0, Label("test", "zmx"))
//     _    <- histogram("test-zmx", 4.0, 1.0, Label("test", "zmx"))
//     _    <- listen(instrument)
//     r004 <- write004(someExternalRegistry)
//   } yield r004
//
//   val program = for {
//     s      <- sendOnTimeout
//     _      <- putStrLn(s)
//     server <- http(someExternalRegistry, config.port.getOrElse(9090))
//   } yield server
//
//   def main(args: Array[String]): Unit = {
//     val server = Runtime.default.unsafeRun(program.provideSomeLayer[Console with Clock](zio.zmx.statsd.live(config)))
//     Thread.sleep(60000)
//     server.stop()
//   }
}
