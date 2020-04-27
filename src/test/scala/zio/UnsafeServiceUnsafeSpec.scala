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
import zio.zmx.Metric._
import zio.zmx.Tag
import zio.duration.Duration
import java.util.concurrent.TimeUnit

object UnsafeServiceUnsafeSpec {

  def main(args: Array[String]): Unit = {
    val ringUnsafeService: RingUnsafeService = {
      val config = new MetricsConfig(20, 5, Duration(5, TimeUnit.SECONDS), None, None)
      new RingUnsafeService(config)
    }
    val hooks = ringUnsafeService.listenUnsafe()
    ringUnsafeService.send(Counter("test-zmx", 1.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
    ringUnsafeService.send(Counter("test-zmx", 3.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
    ringUnsafeService.send(Counter("test-zmx", 1.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
    ringUnsafeService.send(Counter("test-zmx", 5.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
    ringUnsafeService.send(Counter("test-zmx", 4.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
    ringUnsafeService.send(Counter("test-zmx", 6.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))

    val b = ringUnsafeService.send(Counter("test-zmx", 2.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
    println(s"send 7th item: $b")
    Thread.sleep(15000)
    println("Bye!")
    hooks._1.cancel(true)
    hooks._2.cancel(true)
    ()
  }

}
