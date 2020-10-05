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

import zio._

package object parser {

  type ZMXParser = Has[ZMXParser.Service]

  object ZMXParser {

    trait Service {
      def printResponse(response: ZMXProtocol.Response): String
      def parseRequest(command: String): Either[ZMXProtocol.Error, ZMXProtocol.Request]
    }

    val respParser: ULayer[ZMXParser] = ZLayer.succeed(
      new Service {
        def printResponse(response: ZMXProtocol.Response): String =
          ResponsePrinter.asString(response)

        def parseRequest(command: String): Either[ZMXProtocol.Error, ZMXProtocol.Request] =
          RequestParser.fromString(command)
      }
    )

    def printResponse(response: ZMXProtocol.Response): URIO[ZMXParser, String] =
      ZIO.access(_.get.printResponse(response))

    def parseRequest(command: String): ZIO[ZMXParser, ZMXProtocol.Error, ZMXProtocol.Request] =
      ZIO.accessM(z => ZIO.fromEither(z.get.parseRequest(command)))
  }

}
