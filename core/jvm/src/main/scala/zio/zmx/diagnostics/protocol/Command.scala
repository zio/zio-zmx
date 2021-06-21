package zio.zmx.diagnostics.protocol

import zio.Chunk

sealed trait Message

object Message {

  sealed trait Command extends Message

  object Command {
    case object ExecutionMetrics extends Command
    case object FiberDump        extends Command
    case object Test             extends Command

    def fromString(string: String): Option[Command] =
      string match {
        case "metrics" => Some(ExecutionMetrics)
        case "dump"    => Some(FiberDump)
        case "test"    => Some(Test)
        case _         => None
      }
  }

  sealed trait Data extends Message {
    def render: String
  }

  object Data {
    final case class ExecutionMetrics(metrics: zio.internal.ExecutionMetrics) extends Data {
      def render: String = Seq(
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
      def render: String = dumps.mkString("\n")
    }

    final case class Simple(message: String) extends Data {
      def render: String = message
    }
  }

  sealed trait Error extends Message

  object Error {
    final case class InvalidRequest(error: String)   extends Error
    final case class MalformedRequest(error: String) extends Error
    final case class UnknownCommand(command: String) extends Error
  }
}
