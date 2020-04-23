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
import java.io.IOException

import zio._
import zio.clock._
import zio.console._
import zio.nio.core.{ Buffer, ByteBuffer, InetSocketAddress, SocketAddress }
import zio.nio.core.channels._
import zio.nio.core.channels.SelectionKey.Operation

object ZMXServer {
  val BUFFER_SIZE = 256

  final val getCommand: PartialFunction[ZMXServerRequest, ZMXCommands] = {
    case ZMXServerRequest(command, None) if command.equalsIgnoreCase("dump") => ZMXCommands.FiberDump
    case ZMXServerRequest(command, None) if command.equalsIgnoreCase("test") => ZMXCommands.Test
    case ZMXServerRequest(command, None) if command.equalsIgnoreCase("stop") => ZMXCommands.Stop
  }

  private def handleCommand(command: ZMXCommands): UIO[ZMXMessage] =
    command match {
      case ZMXCommands.FiberDump =>
        for {
          dumps  <- Fiber.dumpAll
          result <- URIO.foreach(dumps)(_.prettyPrintM)
        } yield ZMXFiberDump(result)
      case ZMXCommands.Test => ZIO.succeed(ZMXSimple("This is a TEST"))
      case _                => ZIO.succeed(ZMXSimple("Unknown Command"))
    }

  private def processCommand(received: String): IO[Exception, ZMXCommands] = {
    val request: Option[ZMXServerRequest] = ZMXProtocol.parseRequest(received)
    ZIO.fromOption(request.map(getCommand)).mapError(_ => new RuntimeException("Unknown command"))
  }

  private def responseReceived(client: SocketChannel): ZIO[Console, Exception, ByteBuffer] =
    for {
      buffer   <- Buffer.byte(256)
      _        <- client.read(buffer)
      _        <- buffer.flip
      received <- ZMXProtocol.ByteBufferToString(buffer)
      command  <- processCommand(received)
      _        <- putStrLn("received command: " + command)
      message  <- handleCommand(command)
      reply    <- ZMXProtocol.generateReply(message, Success)
      m        <- ZMXProtocol.writeToClient(buffer, client, reply)
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

  def apply(config: ZMXConfig): ZIO[Clock with Console, Exception, Unit] = {

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
        _            <- putStrLn("waiting for connection...")
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
      address  <- addressIo
      selector <- Selector.make
      channel  <- server(address, selector)
      _        <- serverLoop(selector, channel).forever
    } yield ()

  }
}
