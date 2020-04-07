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

package zio.zmx

import java.nio.charset.StandardCharsets

import zio.{ Chunk, ZIO }
import zio.console._
import zio.nio.core.{ Buffer, SocketAddress }
import zio.nio.core.channels.SocketChannel

class ZMXClient(config: ZMXConfig) {

  def sendCommand(args: List[String]): ZIO[Console, Exception, String] = {
    val sending: String = ZMXProtocol.generateRespCommand(args)
    sendMessage(sending)
  }

  def sendMessage(message: String): ZIO[Console, Exception, String] =
    for {
      buffer   <- Buffer.byte(256)
      addr     <- SocketAddress.inetSocketAddress(config.host, config.port)
      client   <- SocketChannel.open(addr)
      b        <- Buffer.byte(Chunk.fromArray(message.getBytes(StandardCharsets.UTF_8)))
      _        <- client.write(b)
      _        <- client.read(buffer)
      response <- ZMXProtocol.ByteBufferToString(buffer)
      _        <- putStrLn(s"Response: ${response}")
      _        <- buffer.clear
    } yield response
}
