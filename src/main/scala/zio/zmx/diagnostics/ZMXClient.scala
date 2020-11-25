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
import zio.zmx.diagnostics.parser.Resp
import zio.{ Chunk, Task, ZIO, ZManaged }

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

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
    def drainChannel(acc: Chunk[Byte], channel: SocketChannel, buffer: ByteBuffer): Task[Chunk[Byte]] =
      for {
        bytesRead <- Task(channel.read(buffer))
        resp      <- if (bytesRead != -1)
                       for {
                         _     <- Task(buffer.flip)
                         resp0 <- Task(acc ++ Chunk.fromByteBuffer(buffer))
                         _     <- Task(buffer.clear)
                         resp  <- drainChannel(resp0, channel, buffer)
                       } yield resp
                     else ZIO.succeed(acc)
      } yield resp

    def sendAndReceive(channel: SocketChannel): Task[String] =
      for {
        _      <- Task(channel.write(ByteBuffer.wrap(req.toArray)))
        buffer <- Task(ByteBuffer.allocate(256))
        resp   <- drainChannel(Chunk.empty, channel, buffer)
      } yield StandardCharsets.UTF_8.decode(ByteBuffer.wrap(resp.toArray)).toString

    for {
      addr <- Task(new InetSocketAddress(config.host, config.port))
      resp <- ZManaged.makeEffect(SocketChannel.open(addr))(_.close()).use(sendAndReceive)
    } yield resp
  }
}
