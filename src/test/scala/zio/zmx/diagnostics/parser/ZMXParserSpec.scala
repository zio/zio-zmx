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

package zio.zmx.diagnostics.parser

import zio.zmx.diagnostics.ZMXProtocol._
import zio.test.Assertion._
import zio.test._

object ZMXParserSpec extends DefaultRunnableSpec {
  def spec =
    suite("ZMXParserSpec")(
      suite("Using the ResponsePrinter")(
        test("zmx test generating a simple response") {
          val value = ResponsePrinter.asString(Response.Success(Data.Simple("foobar")))
          assert(value)(equalTo("+foobar"))

        },
        test("zmx test generating a fiber dump list response") {
          val d     = List("foo", "bar", "baz")
          val value = ResponsePrinter.asString(Response.Success(Data.FiberDump(d)))
          assert(value)(equalTo("*3\r\n+foo\r\n+bar\r\n+baz\r\n"))
        },
        test("zmx test generating a fail response") {
          val value = ResponsePrinter.asString(Response.Fail(Error.UnknownCommand("foobar")))
          assert(value)(equalTo("-foobar"))
        }
      ),
      suite("Using the RequestParser")(
        test("zmx test parsing a fiber dump request") {
          val value = RequestParser.fromString("*1\r\n$4\r\ndump\r\n")
          assert(value)(equalTo(Right(Request(Command.FiberDump, None))))
        },
        test("zmx test parsing a test request") {
          val value = RequestParser.fromString("*1\r\n$4\r\ntest\r\n")
          assert(value)(equalTo(Right(Request(Command.Test, None))))
        },
        test("zmx test parsing an unknown command") {
          val value = RequestParser.fromString("*1\r\n$3\r\nfoo\r\n")
          assert(value)(equalTo(Left(Error.UnknownCommand("foo"))))
        },
        test("zmx test parsing an missing command") {
          val value = RequestParser.fromString("")
          assert(value)(equalTo(Left(Error.MalformedRequest(""))))
        }
      )
      //TODO add fromString tests
    )
}
