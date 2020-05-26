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

import java.nio.channels.{ CancelledKeyException, SocketChannel => JSocketChannel }
import java.nio.charset.StandardCharsets
import java.io.IOException

import zio._
import zio.clock._
import zio.console._
import zio.nio.core.{ Buffer, ByteBuffer, InetSocketAddress, SocketAddress }
import zio.nio.core.channels._
import zio.nio.core.channels.SelectionKey.Operation

object Codec {

  def StringToByteBuffer(message: String): UIO[ByteBuffer] =
    Buffer.byte(Chunk.fromArray(message.getBytes(StandardCharsets.UTF_8)))

  def ByteBufferToString(bytes: ByteBuffer): IO[Exception, String] =
    bytes.getChunk().map(_.map(_.toChar).mkString)

}

trait ZMXServer {
  def shutdown: IO[Exception, Unit]
}

private[zmx] object ZMXServer {
  val BUFFER_SIZE = 256
  
  private def handleCommand(command: ZMXProtocol.Command): UIO[ZMXProtocol.Message] =
    command match {
      case ZMXProtocol.Command.FiberDump =>
        for {
          dumps  <- Fiber.dumpAll
          result <- URIO.foreach(dumps)(_.prettyPrintM)
        } yield ZMXProtocol.Message.FiberDump(result)
      case ZMXProtocol.Command.Test => ZIO.succeed(ZMXProtocol.Message.Simple("This is a TEST"))
      case _                => ZIO.succeed(ZMXProtocol.Message.Simple("Unknown Command"))
    }

  private def responseReceived(client: SocketChannel): ZIO[Console, Exception, ByteBuffer] =
    for {
      buffer   <- Buffer.byte(256)
      _        <- client.read(buffer)
      _        <- buffer.flip
      received <- Codec.ByteBufferToString(buffer)
      command  <- RespZMXParser.fromString(received).mapError(e => new RuntimeException("Unknown command:/n" + e.command))
      _        <- putStrLn("received command: " + command)
      message  <- handleCommand(command)
      reply    = RespZMXParser.asString(message, Success)
      replyBuf <- Codec.StringToByteBuffer(reply)
      m        <- writeToClient(buffer, client, replyBuf)
    } yield m

  private def safeStatusCheck(
    statusCheck: IO[CancelledKeyException, Boolean]
  ): ZIO[Clock with Console, Nothing, Boolean] =
    statusCheck.either.map(_.getOrElse(false))

  private def server(addr: InetSocketAddress, selector: Selector): IO[IOException, ServerSocketChannel] =
    for {
      channel <- ServerSocketChannel.open
      _       <- channel.bind(addr)
      _       <- channel.configureBlocking(false)
      ops     <- channel.validOps
      _       <- channel.register(selector, ops)
    } yield channel

  private def writeToClient(buffer: ByteBuffer, client: SocketChannel, message: ByteBuffer): IO[Exception, ByteBuffer] =
    for {
      _ <- buffer.flip
      _ <- client.write(message)
      _ <- buffer.clear
      _ <- client.close
    } yield message

  def make(config: ZMXConfig): ZIO[Clock with Console, Exception, ZMXServer] = {

    val addressIo = SocketAddress.inetSocketAddress(config.host, config.port)

    def serverLoop(
      selector: Selector,
      channel: ServerSocketChannel
    ): ZIO[Clock with Console, Exception, Unit] = {

      def whenIsAcceptable(key: SelectionKey): ZIO[Clock with Console, IOException, Unit] =
        ZIO.whenM(safeStatusCheck(key.isAcceptable)) {
          for {
            clientOpt <- channel.accept
            client    = clientOpt.get
            _         <- client.configureBlocking(false)
            _         <- client.register(selector, Operation.Read)
            _         <- putStrLn("connection accepted")
          } yield ()
        }

      def whenIsReadable(key: SelectionKey): ZIO[Clock with Console, Exception, Unit] =
        ZIO.whenM(safeStatusCheck(key.isReadable)) {
          for {
            sClient <- key.channel
            _ <- Managed
                  .make(IO.effectTotal(new SocketChannel(sClient.asInstanceOf[JSocketChannel])))(_.close.orDie)
                  .use { client =>
                    for {
                      _ <- responseReceived(client)
                    } yield ()
                  }
          } yield ()
        }

      for {
        _            <- putStrLn("ZIO-ZMX Diagnostics server waiting for requests...")
        _            <- selector.select
        selectedKeys <- selector.selectedKeys
        _ <- ZIO.foreach_(selectedKeys) { key =>
              whenIsAcceptable(key) *>
                whenIsReadable(key) *>
                selector.removeKey(key)
            }
      } yield ()
    }

    for {
      addr     <- addressIo
      selector <- Selector.make
      channel  <- server(addr, selector)
      _        <- putStrLn("ZIO-ZMX Diagnostics server started...")
      _        <- serverLoop(selector, channel).forever.forkDaemon
    } yield new ZMXServer {
      val shutdown = channel.close
    }

  }
}
