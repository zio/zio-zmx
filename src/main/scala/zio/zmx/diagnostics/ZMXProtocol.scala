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

object ZMXProtocol {

  final case class Request(
    command: Command,
    args: Option[List[String]]
  )

  sealed trait Response

  object Response {
    final case class Success(data: Data) extends Response
    final case class Fail(error: Error)  extends Response
  }

  sealed trait Message
  sealed trait Command extends Message

  object Command {
    case object Test      extends Command
    case object FiberDump extends Command
  }

  sealed trait Data extends Message

  object Data {
    final case class Simple(message: String) extends Data {
      override def toString: String = message
    }

    final case class FiberDump(dumps: List[String]) extends Data {
      override def toString: String = dumps.mkString("\n")
    }
  }

  sealed trait Error extends Message

  object Error {
    case class UnknownCommand(command: String)   extends Error
    case class MalformedRequest(command: String) extends Error
  }

}
