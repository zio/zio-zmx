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
import zio.test.TestAspect.sequential
import zio.test._
import zio.zmx.Metrics._
import zio.zmx.Tag
import zio.duration._
import zio.zmx._

object UnsafeServiceSpec extends DefaultRunnableSpec {

  val ringUnsafeService: RingUnsafeService = {
    val config = new MetricsConfig(20, 5, 5.seconds, None, None)
    new RingUnsafeService(config)
  }

  def spec =
    suite("UnsafeService Spec")(
      suite("Using the UnsafeService directly")(
        zio.test.test("send returns true") {
          val b = ringUnsafeService.counter("test-zmx", 2.0, 1.0, Tag("test", "zmx"))
          println(s"send 7th item: $b")
          assert(b)(equalTo(true))
        },
        testM("Send on 5") {
          ringUnsafeService.counter("test-zmx", 1.0, 1.0, Tag("test", "zmx"))
          ringUnsafeService.counter("test-zmx", 3.0, 1.0, Tag("test", "zmx"))
          ringUnsafeService.counter("test-zmx", 1.0, 1.0, Tag("test", "zmx"))
          ringUnsafeService.counter("test-zmx", 5.0, 1.0, Tag("test", "zmx"))
          ringUnsafeService.counter("test-zmx", 4.0, 1.0, Tag("test", "zmx"))
          ringUnsafeService.counter("test-zmx", 2.0, 1.0, Tag("test", "zmx"))
          for {
            lngs <- ringUnsafeService.collect(ringUnsafeService.udp)
          } yield assert(lngs.size)(equalTo(5)) && assert(lngs.sum)(equalTo(60L))
        },
        testM("Send 3 on timeout") {
          ringUnsafeService.counter("test-zmx", 1.0, 1.0, Tag("test", "zmx"))
          ringUnsafeService.counter("test-zmx", 3.0, 1.0, Tag("test", "zmx"))
          ringUnsafeService.counter("test-zmx", 5.0, 1.0, Tag("test", "zmx"))
          for {
            _    <- ringUnsafeService.poll
            _    <- ringUnsafeService.poll
            _    <- ringUnsafeService.poll
            lngs <- ringUnsafeService.sendIfNotEmpty(ringUnsafeService.udp)
          } yield assert(lngs.size)(isGreaterThanEqualTo(3)) && assert(lngs.sum)(isGreaterThanEqualTo(36L))
        }
      ) @@ sequential
    )

}
