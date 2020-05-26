package zio.zmx.server.parser

import zio.{ IO, ZIO }
import zio.zmx.server._
import scala.annotation.tailrec

private [parser] object RespZMXParser {

  /**
   *  Implementation of the RESP protocol to be used by ZMX for client-server communication
   *
   *  RESP Protocol Specs: https://redis.io/topics/protocol
   *
   */
  /** Response types */
  val MULTI = "*"
  val PASS  = "+"
  val FAIL  = "-"
  val BULK  = "$"

  def asString(message: ZMXProtocol.Message, replyType: ZMXServerResponse): String = {

    def asResp(message: ZMXProtocol.Message): String = message match {
      case ZMXProtocol.Message.FiberDump(dumps) =>
        s"$MULTI${dumps.length}\r\n${dumps.map(d => s"$PASS${d.trim()}\r\n").mkString}" //array eg. "*3\r\n:1\r\n:2\r\n:3\r\n";
      case ZMXProtocol.Message.Simple(message) => s"+${message}"
    }

    replyType match {
      case Success => asResp(message)
      case Fail    => s"-${message}"
    }
  }

  def fromString(received: String): IO[UnknownZMXCommand, ZMXProtocol.Command] = {
    val request: Option[ZMXServerRequest] = parseRequest(received)
    ZIO.fromOption(request.map(getCommand)).mapError(_ => UnknownZMXCommand(received))
  }

  private final val numberOfBulkStrings: PartialFunction[String, Int] = {
    case s: String if s startsWith MULTI => s.slice(1, s.length).toInt
  }

  private final val getBulkString: PartialFunction[(List[String], Int), String] = {
    case (s, d) if s.nonEmpty && d > 0 && s(1).length == d => s(1)
  }

  private final val sizeOfBulkString: PartialFunction[String, Int] = {
    case s: String if s startsWith BULK => s.slice(1, s.length).toInt
  }

  @tailrec
  private final def getArgs(received: List[String], acc: List[String] = List()): List[String] =
    if (received.size > 1 && (received.head startsWith BULK)) {
      val result: String = getBulkString((received.slice(0, 2), sizeOfBulkString(received.head)))
      getArgs(received.slice(2, received.size), acc :+ result)
    } else
      acc

  /**
   * Extracts command and arguments received from client
   *
   * 1) Check how many bulk strings we received
   * 2) Extract each bulk string
   * 3) First bulk string is the command
   * 4) Subsequent bulk strings are the arguments to the command
   *
   * Sample: "*2\\r\\n\$3\\r\\nfoo\\r\\n\$3\\r\\nbar\\r\\n"
   *
   */
  private def parseRequest(req: String): Option[ZMXServerRequest] = {
    val receivedList: List[String] = req.split("\r\n").toList
    val multiCount: Int            = numberOfBulkStrings(receivedList(0))
    if (multiCount > 0) {
      val command: String = getBulkString((receivedList.slice(1, 3), sizeOfBulkString(receivedList(1))))
      println("as command: " + command)
      if (receivedList.length < 4)
        Some(
          ZMXServerRequest(
            command = command,
            args = None
          )
        )
      else
        Some(
          ZMXServerRequest(
            command = command,
            args = Some(getArgs(receivedList.slice(3, receivedList.size)))
          )
        )
    } else None
  }

  private final val getCommand: PartialFunction[ZMXServerRequest, ZMXProtocol.Command] = {
    case ZMXServerRequest(command, None) if command.equalsIgnoreCase("dump") => ZMXProtocol.Command.FiberDump
    case ZMXServerRequest(command, None) if command.equalsIgnoreCase("test") => ZMXProtocol.Command.Test
    case ZMXServerRequest(command, None) if command.equalsIgnoreCase("stop") => ZMXProtocol.Command.Stop
  }

}