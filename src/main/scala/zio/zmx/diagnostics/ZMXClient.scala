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

import java.nio.charset.StandardCharsets
import java.nio.charset.StandardCharsets._

import zio.console._
import zio.nio.channels.SocketChannel
import zio.nio.core.{ Buffer, ByteBuffer, SocketAddress }
import zio.{ Chunk, Task, ZIO }

object ZMXClient {

  /**
   * Generate message to send to server
   */
  def generateRespCommand(args: List[String]): String = {
    val protocol = new StringBuilder().append("*").append(args.length).append("\r\n")

    args.foreach { arg =>
      val length = arg.getBytes(UTF_8).length
      protocol.append("$").append(length).append("\r\n").append(arg).append("\r\n")
    }

    protocol.result
  }

}

class ZMXClient(config: ZMXConfig) {

  def sendCommand(args: List[String]): ZIO[Console, Exception, String] = {
    val sending: String = ZMXClient.generateRespCommand(args)
    nioRequestResponse(sending).refineToOrDie[Exception]
  }

  private def nioRequestResponse(req: String): Task[String] = {
    def drainBuffer(acc: Chunk[String], buffer: ByteBuffer): Task[Chunk[String]] =
      for {
        hasRemaining <- buffer.hasRemaining
        result       <- if (hasRemaining)
                          buffer
                            .withJavaBuffer(buf => ZIO.succeed(acc :+ StandardCharsets.UTF_8.decode(buf).toString))
                            .flatMap(drainBuffer(_, buffer))
                        else ZIO.succeed(acc)
      } yield result

    def drainChannel(acc: Chunk[String], channel: SocketChannel, buffer: ByteBuffer): Task[Chunk[String]] =
      for {
        bytesRead <- channel.read(buffer)
        resp      <- if (bytesRead != -1)
                       for {
                         _     <- buffer.flip
                         resp0 <- drainBuffer(acc, buffer)
                         _     <- buffer.clear
                         resp  <- drainChannel(resp0, channel, buffer)
                       } yield resp
                     else ZIO.succeed(acc)
      } yield resp

    def sendAndReceive(channel: SocketChannel): Task[String] =
      for {
        _      <- channel.write(Chunk.fromArray(req.getBytes(StandardCharsets.UTF_8)))
        buffer <- Buffer.byte(256)
        resp   <- drainChannel(Chunk.empty, channel, buffer)
      } yield resp.mkString

    for {
      addr <- SocketAddress.inetSocketAddress(config.host, config.port)
      resp <- SocketChannel.open(addr).use(sendAndReceive)
    } yield resp
  }

}
