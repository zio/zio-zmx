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

import zio.Chunk

object ZMXProtocol {

  final case class Request(
    command: Command,
    args: Option[Chunk[String]]
  )

  sealed trait Response

  object Response {
    final case class Fail(error: Error)  extends Response
    final case class Success(data: Data) extends Response
  }

  sealed trait Message
  sealed trait Command extends Message

  object Command {
    final case object ExecutionMetrics extends Command
    final case object FiberDump        extends Command
    final case object Test             extends Command

    val fromString: String => Option[Command] = {
      case "metrics" => Some(ExecutionMetrics)
      case "dump"    => Some(FiberDump)
      case "test"    => Some(Test)
      case _         => None
    }
  }

  sealed trait Data extends Message

  object Data {
    final case class ExecutionMetrics(metrics: zio.internal.ExecutionMetrics) extends Data {
      override def toString: String = Seq(
        render("concurrency", metrics.concurrency.toString),
        render("capacity", metrics.capacity.toString),
        render("size", metrics.size.toString),
        render("enqueued_count", metrics.enqueuedCount.toString),
        render("dequeued_count", metrics.dequeuedCount.toString),
        render("workers_count", metrics.workersCount.toString)
      ).mkString("\r\n")

      private def render(key: String, value: String): String =
        s"$key:$value"
    }

    final case class FiberDump(dumps: Chunk[String]) extends Data {
      override def toString: String = dumps.mkString("\n")
    }

    final case class Simple(message: String) extends Data {
      override def toString: String = message
    }
  }

  sealed trait Error extends Message

  object Error {
    final case class InvalidRequest(error: String)   extends Error
    final case class MalformedRequest(error: String) extends Error
    final case class UnknownCommand(command: String) extends Error
  }

}
