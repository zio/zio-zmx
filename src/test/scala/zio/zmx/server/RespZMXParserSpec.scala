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

package zio.zmx.server

import zio.test.Assertion._
import zio.test._

object RespZMXParserSpec extends DefaultRunnableSpec {
  def spec =
    suite("RespZMXParserSpec")(
      suite("Using the RESP ZMX parser")(
        test("zmx test generating a successful command") {
          val p: String = ZMXClient.generateRespCommand(args = List("foobar"))
          assert(p)(equalTo("*1\r\n$6\r\nfoobar\r\n"))
        },
        test("zmx test generating a successful multiple command") {
          val p: String = ZMXClient.generateRespCommand(args = List("foo", "bar"))
          assert(p)(equalTo("*2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n"))
        },
        test("zmx test generating a successful empty command") {
          val p: String = ZMXClient.generateRespCommand(args = List())
          assert(p)(equalTo("*0\r\n"))
        },
        test("zmx test generating a simple reply") {
          val value = RespZMXParser.asString(ZMXProtocol.Message.Simple("foobar"), Success)
          assert(value)(equalTo("+foobar"))

        },
        test("zmx test generating a fiber dump list reply") {
          val d                  = List("foo", "bar", "baz")
          val value = RespZMXParser.asString(ZMXProtocol.Message.FiberDump(d), Success)
          assert(value)(equalTo("*3\r\n+foo\r\n+bar\r\n+baz\r\n"))
        },
        test("zmx test generating a fail reply") {
          val value = RespZMXParser.asString(ZMXProtocol.Message.Simple("foobar"), Fail)
          assert(value)(equalTo("-foobar"))
        }
      )
      //TODO add fromString tests
    )
}
