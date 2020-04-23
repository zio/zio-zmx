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
import zio.zmx.Metric._
import zio.zmx.Tag
import zio.test.environment.TestClock
import zio.duration.Duration

import scala.concurrent.duration._

object UnsafeServiceSpec extends DefaultRunnableSpec {

  def spec =
    suite("UnsafeService Spec")(
      suite("Using the UnsafeService directly")(
        test.test("send returns true") {
          val b = UnsafeService.send(Counter("test-zmx", 2.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
          println(s"send 7th item: $b")
          assert(b)(equalTo(true))
        },
        test.testM("Send on 5") {
          UnsafeService.send(Counter("test-zmx", 1.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
          UnsafeService.send(Counter("test-zmx", 3.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
          UnsafeService.send(Counter("test-zmx", 1.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
          UnsafeService.send(Counter("test-zmx", 5.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
          UnsafeService.send(Counter("test-zmx", 4.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
          UnsafeService.send(Counter("test-zmx", 2.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
          for {
            lngs <- UnsafeService.collect(UnsafeService.udp)
          } yield assert(lngs.size)(equalTo(5)) && assert(lngs.sum)(equalTo(60L))
        },
        test.testM("Send 3 on timeout") {
          UnsafeService.send(Counter("test-zmx", 1.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
          UnsafeService.send(Counter("test-zmx", 3.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
          UnsafeService.send(Counter("test-zmx", 5.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
          for {
            _    <- UnsafeService.poll
            _    <- TestClock.setTime(Duration.fromScala(70.millis))
            _    <- UnsafeService.poll
            _    <- TestClock.setTime(Duration.fromScala(100.millis))
            _    <- UnsafeService.poll
            _    <- TestClock.setTime(Duration.fromScala(140.millis))
            lngs <- UnsafeService.sendIfNotEmpty(UnsafeService.udp)
          } yield assert(lngs.size)(equalTo(3)) && assert(lngs.sum)(equalTo(36L))
        }
      )
    )

}
