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
import zio.random.Random

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

  sealed abstract class CliCmd(val in: Chunk[String], val assertion: Assertion[String])
  case object Test extends CliCmd(Chunk("test"), equalTo("+This is a TEST"))
  case object Unknown extends CliCmd(Chunk("unknown"), equalTo("-unknown"))
  case object Dump extends CliCmd(Chunk("dump"), equalTo("*0\r\n"))
  case object Metrics extends CliCmd(Chunk("metrics"),
    containsString("capacity:") && containsString("concurrency:"))

  private val genCliCmd: Gen[Random with Sized, CliCmd] = Gen.oneOf(
    Gen.const[CliCmd](Test),
    Gen.const[CliCmd](Unknown),
    Gen.const[CliCmd](Dump),
    Gen.const[CliCmd](Metrics)
  )

  private val Parallelism = 5
  private val Iterations = 1000

  def spec =
    suite("ZMXServerSpec")(
      testM(s"query zmx server in parallel and receive all correct answers") {
        ZIO.mergeAllParN(Parallelism) {
          Stream.continually(()).take(Iterations).map(_ =>
            for {
              cmd <- genCliCmd.runHead.get
              out <- zmxClient.sendCommand(cmd.in)
            } yield assert(out)(cmd.assertion))
        }(assertCompletes)(_ && _)
      }
    )
}
