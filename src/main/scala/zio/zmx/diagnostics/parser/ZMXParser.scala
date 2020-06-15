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

import zio.zmx.diagnostics._
import scala.annotation.tailrec
import scala.util.Try

trait RESPProtocol {

  /** Data types */
  val MULTI = "*"
  val PASS  = "+"
  val FAIL  = "-"
  val BULK  = "$"

}

/**
 *  Implementation of the RESP protocol to be used by ZMX for client-server communication
 *
 *  RESP Protocol Specs: https://redis.io/topics/protocol
 *
 */
private[parser] object ResponsePrinter extends RESPProtocol {

  def asString(resp: ZMXProtocol.Response): String = resp match {

    case ZMXProtocol.Response.Success(data) =>
      data match {
        case ZMXProtocol.Data.FiberDump(dumps) =>
          s"$MULTI${dumps.length}\r\n${dumps.map(d => s"$PASS${d.trim()}\r\n").mkString}" //array eg. "*3\r\n:1\r\n:2\r\n:3\r\n";
        case ZMXProtocol.Data.Simple(message) =>
          s"+${message}"
      }

    case ZMXProtocol.Response.Fail(error) =>
      error match {
        case ZMXProtocol.Error.UnknownCommand(cmd) => s"-${cmd}"
        case ZMXProtocol.Error.MalformedRequest(_) => "-$MALFORMED_REQUEST"
      }

  }
}

private[parser] object RequestParser extends RESPProtocol {

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
  def fromString(req: String): Either[ZMXProtocol.Error, ZMXProtocol.Request] = {
    val receivedList: List[String] = req.split("\r\n").toList
    numberOfBulkStrings(receivedList(0)) match {
      case Some(m) if m > 0 =>
        val command: String = getBulkString((receivedList.slice(1, 3), sizeOfBulkString(receivedList(1))))
        val cmd             = getCommand(command)
        println("as command: " + command)
        if (receivedList.length < 4)
          cmd.map(c =>
            ZMXProtocol.Request(
              command = c,
              args = None
            )
          )
        else
          cmd.map(c =>
            ZMXProtocol.Request(
              command = c,
              args = Some(getArgs(receivedList.slice(3, receivedList.size)))
            )
          )
      case None =>
        Left(ZMXProtocol.Error.MalformedRequest(req))
    }
  }

  private final val getCommand: String => Either[ZMXProtocol.Error.UnknownCommand, ZMXProtocol.Command] = {
    case "dump" => Right(ZMXProtocol.Command.FiberDump)
    case "test" => Right(ZMXProtocol.Command.Test)
    case c      => Left(ZMXProtocol.Error.UnknownCommand(c))
  }

  private final val numberOfBulkStrings: String => Option[Int] = {
    case s: String if s startsWith MULTI => Try(s.slice(1, s.length).toInt).toOption
    case _                               => None
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

}
