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

package zio.zmx.diagnostics

import zio.console._
import zio.test.Assertion.{containsString, equalTo}
import zio.test.{testM, _}
import zio.zmx.Diagnostics
import zio.{Chunk, ZIO}
import zio.Runtime

object ZMXServerSpec extends DefaultRunnableSpec {

  private def zmxConfig = ZMXConfig.empty
  private val zmxClient = new ZMXClient(zmxConfig)

  private val program: ZIO[Console, Throwable, Unit] =
    for {
      _ <- putStrLn("Waiting for input")
      a <- getStrLn
      _ <- putStrLn("Thank you for " + a)
    } yield ()

  private val programWithDiagnostics =
    program.provideCustomLayer(Diagnostics.live(zmxConfig))

  Runtime.default.unsafeRunAsync(programWithDiagnostics)(_ => ())

  def spec =
    suite("ZMXServerSpec")(
        testM("answer to test command") {
          for {
            out <- zmxClient.sendCommand(Chunk("test"))
          } yield assert(out)(equalTo("+This is a TEST"))
        },
     testM("unswer to unknown command") {
        for {
          out <- zmxClient.sendCommand(Chunk("unknown"))
        } yield assert(out)(equalTo("-unknown"))
      },
      testM("answer to dump command") {
        for {
          out <- zmxClient.sendCommand(Chunk("dump"))
        } yield assert(out)(equalTo("*0\r\n"))
      },
      testM("answer to metrics command") {
        for {
          out <- zmxClient.sendCommand(Chunk("metrics"))
        } yield assert(out)(containsString("capacity:") && containsString("concurrency:"))
//        } yield assert(out)(matchesRegex("$101\ncapacity:[0-9]+\nconcurrency:[0-9]+\ndequeued_count:[0-9]+\nenqueued_count:[0-9]+\nsize:[0-9]+\nworkers_count:[0-9]+\r\n"))
      },
      testM("answer too many commands in parallel") { // TODO generator
        for {
          out <- ZIO.foreachParN(5)(Stream.continually(()).take(1000).toList)(_ => zmxClient.sendCommand(Chunk("test")))
          set = out.toSet
          size = out.size
        } yield assert(set)(equalTo(Set("+This is a TEST"))) && assert(size)(equalTo(1000))
      }
    )
}
