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
import zio.test.TestAspect._
import zio.test._
import zio.zmx.Metrics._
import zio.zmx.Label
import zio.duration._
import zio.zmx._

object UnsafeServiceSpec extends DefaultRunnableSpec {

  def ringUnsafeService(runtime: Runtime[Any]): RingUnsafeService = {
    val config = MetricsConfig(
      maximumSize = 20,
      bufferSize = 5,
      timeout = 5.seconds,
      pollRate = 100.millis,
      host = None,
      port = None
    )
    runtime.unsafeRun {
      Ref
        .make[Chunk[Metric[_]]](Chunk.empty)
        .map(aggregator => new RingUnsafeService(config, aggregator))
    }
  }

  def spec =
    suite("UnsafeService Spec")(
      suite("Using the UnsafeService directly")(
        zio.test.testM("send returns true") {
          ZIO.runtime.map { runtime: Runtime[Any] =>
            val svc = ringUnsafeService(runtime)
            val b   = svc.counter("test-zmx", 2.0, 1.0, Label("test", "zmx"))
            assert(b)(equalTo(true))
          }
        },
        testM("Send on 5 unsafe") {
          ZIO.runtime.flatMap { runtime: Runtime[Any] =>
            val svc = ringUnsafeService(runtime)
            svc.counter("test-zmx", 1.0, 1.0, Label("test", "zmx"))
            svc.counter("test-zmx", 3.0, 1.0, Label("test", "zmx"))
            svc.counter("test-zmx", 1.0, 1.0, Label("test", "zmx"))
            svc.counter("test-zmx", 5.0, 1.0, Label("test", "zmx"))
            svc.counter("test-zmx", 4.0, 1.0, Label("test", "zmx"))
            svc.counter("test-zmx", 2.0, 1.0, Label("test", "zmx"))
            for {
              lngs <- svc.collect(svc.udp)
            } yield assert(lngs.head.size)(equalTo(5)) && assert(lngs.head.sum)(equalTo(60L))
          }
        },
        testM("Send 3 on timeout unsafe") {
          ZIO.runtime.flatMap { runtime: Runtime[Any] =>
            val svc = ringUnsafeService(runtime)
            svc.counter("test-zmx", 1.0, 1.0, Label("test", "zmx"))
            svc.counter("test-zmx", 3.0, 1.0, Label("test", "zmx"))
            svc.counter("test-zmx", 5.0, 1.0, Label("test", "zmx"))
            for {
              _    <- svc.poll
              _    <- svc.poll
              _    <- svc.poll
              lngs <- svc.collect(svc.udp)
            } yield assert(lngs.head.size)(isGreaterThanEqualTo(3)) && assert(lngs.head.sum)(isGreaterThanEqualTo(36L))
          }
        }
      ) @@ sequential
    )
}
