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

import zio.zmx.ZMXSupervisor
import java.io.IOException
import java.nio.channels.{CancelledKeyException, SocketChannel => JSocketChannel}

import zio._
import zio.clock._
import zio.console._
import zio.internal.Platform
import zio.zmx.diagnostics.nio.InetSocketAddress
import zio.zmx.diagnostics.parser.Parser
import zio.zmx.diagnostics.nio._

private[zmx] object ZMXServer {
  val BUFFER_SIZE = 256

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

  def make(config: ZMXConfig): ZManaged[Clock with Console, Exception, Unit] = {
    val getFiberDumps: UIO[Iterable[Fiber.Dump]] =
      ZMXSupervisor.value.flatMap { fibers =>
        Fiber.dump(fibers.toSeq: _*)
      }

    def handleRequest(parsedRequest: Either[ZMXProtocol.Error, ZMXProtocol.Request]): UIO[ZMXProtocol.Response] =
      parsedRequest.fold(
        error => ZIO.succeed(ZMXProtocol.Response.Fail(error)),
        success => handleCommand(success.command).map(cmd => ZMXProtocol.Response.Success(cmd))
      )

    def handleCommand(command: ZMXProtocol.Command): UIO[ZMXProtocol.Data] =
      command match {
        case ZMXProtocol.Command.ExecutionMetrics =>
          Platform.default.executor.metrics.fold(ZIO.succeed[ZMXProtocol.Data](ZMXProtocol.Data.Simple(""))) {
            metrics =>
              ZIO.succeed[ZMXProtocol.Data](ZMXProtocol.Data.ExecutionMetrics(metrics))
          }
        case ZMXProtocol.Command.FiberDump        =>
          for {
            allDumps <- getFiberDumps
            result   <- IO.foreach(allDumps)(_.prettyPrintM)
          } yield ZMXProtocol.Data.FiberDump(Chunk.fromIterable(result))
        case ZMXProtocol.Command.Test             => ZIO.succeed(ZMXProtocol.Data.Simple("This is a TEST"))
      }

    val addressIo = InetSocketAddress(config.host, config.port)

    def processRequest(
      client: SocketChannel
    ): ZIO[Console, Exception, ByteBuffer] =
      for {
        buffer  <- ByteBuffer.byte(256)
        _       <- client.read(buffer)
        _       <- buffer.flip
        bytes   <- buffer.getChunk()
        request <- Parser.parse(bytes).either
        result  <- handleRequest(request)
        response = Parser.serialize(result)
        message <- ByteBuffer.byte(response)
        output  <- writeToClient(buffer, client, message)
      } yield output

    def serverLoop(
      selector: Selector,
      channel: ServerSocketChannel
    ): ZIO[Clock with Console, Exception, Unit] = {

      def whenIsAcceptable(key: SelectionKey): ZIO[Clock with Console, IOException, Unit] =
        ZIO.whenM(safeStatusCheck(key.isAcceptable)) {
          for {
            client <- channel.accept
            _         <- client.configureBlocking(false)
            _         <- client.register(selector, SocketChannel.OpRead)
            _         <- putStrLn("connection accepted")
          } yield ()
        }

      def whenIsReadable(
        key: SelectionKey
      ): ZIO[Clock with Console, Exception, Unit] =
        ZIO.whenM[Clock with Console, Exception](
          safeStatusCheck(key.isReadable)
        ) {
          for {
            sClient <- key.channel
            _       <- Managed
                         .make(IO.effectTotal(new SocketChannel(sClient.asInstanceOf[JSocketChannel])))(_.close.orDie)
                         .use { client =>
                           for {
                             _ <- processRequest(client)
                           } yield ()
                         }
          } yield ()
        }

      for {
        _            <- putStrLn("ZIO-ZMX Diagnostics server waiting for requests...")
        _            <- selector.select
        selectedKeys <- selector.selectedKeys
        _            <- ZIO.foreach_(selectedKeys) { key =>
                          whenIsAcceptable(key) *>
                            whenIsReadable(key) *>
                            selector.removeKey(key)
                        }
      } yield ()
    }

    val acq = for {
      addr     <- addressIo
      selector <- Selector.make
      channel  <- server(addr, selector)
      _        <- putStrLn("ZIO-ZMX Diagnostics server started...")
      fiber    <- serverLoop(selector, channel).forever.forkDaemon
    } yield (channel, selector, fiber)

    ZManaged
      .make(acq) { case (channel, selector, fiber) => channel.close.orDie *> selector.close.orDie *> fiber.interrupt }
      .unit
  }
}
