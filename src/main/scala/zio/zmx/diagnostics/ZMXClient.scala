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

import zio.console._
import zio.nio.channels.SocketChannel
import zio.nio.core.{ Buffer, ByteBuffer, SocketAddress }
import zio.zmx.diagnostics.parser.Resp
import zio.{ Chunk, Task, ZIO }

object ZMXClient {

  /**
   * Generate message to send to server
   */
  def generateRespCommand(args: Chunk[String]): Chunk[Byte] =
    Resp.Array(args.map(Resp.BulkString)).serialize

}

class ZMXClient(config: ZMXConfig) {

  def sendCommand(args: Chunk[String]): ZIO[Console, Exception, String] =
    nioRequestResponse(ZMXClient.generateRespCommand(args)).refineToOrDie[Exception]

  private def nioRequestResponse(req: Chunk[Byte]): Task[String] = {
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
        _      <- channel.write(req)
        buffer <- Buffer.byte(256)
        resp   <- drainChannel(Chunk.empty, channel, buffer)
      } yield resp.mkString

    for {
      addr <- SocketAddress.inetSocketAddress(config.host, config.port)
      resp <- SocketChannel.open(addr).use(sendAndReceive)
    } yield resp
  }

}
