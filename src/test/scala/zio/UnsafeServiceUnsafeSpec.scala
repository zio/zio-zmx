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

import zio.zmx._
import zio.zmx.Metrics._
import zio.zmx.Label
import zio.duration._
import java.util.concurrent.TimeUnit

import scala.concurrent.Await

object UnsafeServiceUnsafeSpec {

  def main(args: Array[String]): Unit = {
    val ringUnsafeService: RingUnsafeService = {
      val config = MetricsConfig(
        maximumSize = 20,
        bufferSize = 5,
        timeout = Duration(5, TimeUnit.SECONDS),
        pollRate = 100.millis,
        host = None,
        port = None
      )
      Runtime.default.unsafeRun {
        Ref
          .make[Chunk[Metric[_]]](Chunk.empty)
          .map(aggregator => new RingUnsafeService(config, aggregator))
      }
    }
    val hook = ringUnsafeService.listenUnsafe()
    ringUnsafeService.counter("test-zmx", 1.0, 1.0, Label("test", "zmx"))
    ringUnsafeService.counter("test-zmx", 3.0, 1.0, Label("test", "zmx"))
    ringUnsafeService.counter("test-zmx", 1.0, 1.0, Label("test", "zmx"))
    ringUnsafeService.counter("test-zmx", 5.0, 1.0, Label("test", "zmx"))
    ringUnsafeService.counter("test-zmx", 4.0, 1.0, Label("test", "zmx"))
    ringUnsafeService.counter("test-zmx", 6.0, 1.0, Label("test", "zmx"))

    Thread.sleep(15000)
    Await.result(hook.cancel, 5.seconds.asScala)
    ()
  }

}
