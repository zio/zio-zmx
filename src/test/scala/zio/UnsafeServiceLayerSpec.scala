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

//import zio.test.Assertion._
//import zio.test._
//import zio.UIO
import zio.zmx.Metrics._
import zio.zmx.Metric._
import zio.zmx.Tag

object UnsafeServiceSpec {
  def main(args: Array[String]): Unit = {
    UnsafeService.send(Counter("test-zmx", 2.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
    println(Runtime.default.unsafeRun(UnsafeService.listen()))
    UnsafeService.send(Counter("test-zmx", 3.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
    UnsafeService.send(Counter("test-zmx", 1.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
    UnsafeService.send(Counter("test-zmx", 5.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
    UnsafeService.send(Counter("test-zmx", 4.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
    UnsafeService.send(Counter("test-zmx", 6.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))

    val b = UnsafeService.send(Counter("test-zmx", 2.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
    println(s"send 7th item: $b")
    Thread.sleep(20000)
  }

  /*def main(args: Array[String]): Unit = {
    UnsafeService.listenUnsafe()
    println("listening...")
    val p: Unit = UnsafeService.send(Counter("test", 2.0, 1.0, Chunk.fromArray(Array(Tag("test", "zmx")))))
    println(p)
  }*/

}
