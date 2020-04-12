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

import java.nio.charset.StandardCharsets
import java.nio.charset.StandardCharsets._
import zio.Chunk
import zio.nio.core.{ Buffer, ByteBuffer }
import zio.nio.core.channels.{ SocketChannel }
import zio.{ IO, UIO }

import scala.annotation.tailrec

object ZMXProtocol {

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

  /**
   * Generate message to send to server
   *
   *
   */
  def generateRespCommand(args: List[String]): String = {
    val protocol = new StringBuilder().append("*").append(args.length).append("\r\n")

    args.foreach { arg =>
      val length = arg.getBytes(UTF_8).length
      protocol.append("$").append(length).append("\r\n").append(arg).append("\r\n")
    }

    protocol.result
  }

  def serializeMessage(message: ZMXMessage): String = message match {
    case ZMXFiberDump(dumps) =>
      s"$MULTI${dumps.length}\r\n${dumps.map(d => s"$PASS${d.trim()}\r\n").mkString}" //array eg. "*3\r\n:1\r\n:2\r\n:3\r\n";
    case ZMXSimple(message) => s"+${message}"
  }

  /**
   * Generate reply to send back to client
   */
  def generateReply(message: ZMXMessage, replyType: ZMXServerResponse): UIO[ByteBuffer] = {
    val reply: String = replyType match {
      case Success => serializeMessage(message)
      case Fail    => s"-${message}"
    }
    println(s"reply: ${reply}")
    Buffer.byte(Chunk.fromArray(reply.getBytes(StandardCharsets.UTF_8)))
  }

  final val getSuccessfulResponse: PartialFunction[String, String] = {
    case s: String if s startsWith PASS => s.slice(1, s.length)
  }

  final val getErrorResponse: PartialFunction[String, String] = {
    case s: String if s startsWith FAIL => s.slice(1, s.length)
  }

  final val numberOfBulkStrings: PartialFunction[String, Int] = {
    case s: String if s startsWith MULTI => s.slice(1, s.length).toInt
  }

  final val sizeOfBulkString: PartialFunction[String, Int] = {
    case s: String if s startsWith BULK => s.slice(1, s.length).toInt
  }

  final val getBulkString: PartialFunction[(List[String], Int), String] = {
    case (s, d) if s.nonEmpty && d > 0 && s(1).length == d => s(1)
  }

  @tailrec
  final def getArgs(received: List[String], acc: List[String] = List()): List[String] =
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
  def parseRequest(req: String): Option[ZMXServerRequest] = {
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

  /**
   * Extract response received by client
   *
   * Success of format: +<message>
   * Error of format: -<message>
   *
   */
  val clientReceived: PartialFunction[String, String] = getSuccessfulResponse orElse getErrorResponse

  def StringToByteBuffer(message: UIO[String]): UIO[ByteBuffer] =
    for {
      content <- message
      buffer  <- Buffer.byte(Chunk.fromArray(content.getBytes(StandardCharsets.UTF_8)))
    } yield buffer

  def ByteBufferToString(bytes: ByteBuffer): IO[Exception, String] =
    bytes.getChunk().map(_.map(_.toChar).mkString)

  def writeToClient(buffer: ByteBuffer, client: SocketChannel, message: ByteBuffer): IO[Exception, ByteBuffer] =
    for {
      _ <- buffer.flip
      _ <- client.write(message)
      _ <- buffer.clear
      _ <- client.close
    } yield message
}
