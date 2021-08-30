package zio.zmx.diagnostics

import java.io.IOException

import zio._
import zio.clock._
import zio.console._
import zio.internal.Platform
import zio.zmx.diagnostics.parser.Parser
import zio.zmx.diagnostics.nio._
import zio.zmx.diagnostics.protocol._

final case class DiagnosticsLive(config: ZMXConfig) extends Diagnostics {

  val BUFFER_SIZE = 256

  def createServerSocket(addr: InetSocketAddress, selector: Selector): IO[IOException, ServerSocketChannel] =
    for {
      channel <- ServerSocketChannel.open
      _       <- channel.bind(addr)
      _       <- channel.configureBlocking(false)
      ops     <- channel.validOps
      _       <- channel.register(selector, ops)
    } yield channel

  def handleCommand(command: Message.Command): UIO[Message.Data] =
    command match {
      case Message.Command.ExecutionMetrics =>
        Platform.default.executor.metrics.fold(ZIO.succeed[Message.Data](Message.Data.Simple(""))) { metrics =>
          ZIO.succeed[Message.Data](Message.Data.ExecutionMetrics(metrics))
        }
      case Message.Command.FiberDump        =>
        for {
          fibers   <- ZMXSupervisor.value
          allDumps <- IO.foreach(fibers)(_.dump)
          result   <- IO.foreach(allDumps)(_.prettyPrintM)
        } yield Message.Data.FiberDump(Chunk.fromIterable(result))
      case Message.Command.Test             => ZIO.succeed(Message.Data.Simple("This is a TEST"))
    }

  def handleRequest(parsedRequest: Either[Message.Error, Request]): UIO[Response] =
    parsedRequest.fold(
      error => ZIO.succeed(Response.Fail(error)),
      success => handleCommand(success.command).map(cmd => Response.Success(cmd))
    )

  def initialize: ZIO[Clock with Console, Exception, UIO[Any]] =
    for {
      addr     <- InetSocketAddress.make(config.host, config.port)
      selector <- Selector.make
      channel  <- createServerSocket(addr, selector)
      _        <- putStrLn("ZIO-ZMX Diagnostics server started...")
      fiber    <- serverLoop(selector, channel).forever.forkDaemon
    } yield channel.close.orDie *> selector.close.orDie *> fiber.interrupt

  def processRequest(
    client: SocketChannel
  ): ZIO[Console, Exception, ByteBuffer] =
    for {
      buffer  <- ByteBuffer.byte(BUFFER_SIZE)
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
      ZIO.whenM(key.isAcceptable) {
        for {
          client <- channel.accept
          _      <- client.configureBlocking(false)
          _      <- client.register(selector, SocketChannel.OpRead)
          _      <- putStrLn("connection accepted")
        } yield ()
      }

    def whenIsReadable(
      key: SelectionKey
    ): ZIO[Clock with Console, Exception, Unit] =
      ZIO.whenM[Clock with Console, Exception](key.isReadable) {
        for {
          sClient <- key.channel
          _       <- Managed
                       .make(SocketChannel(sClient))(_.close.orDie)
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

  def writeToClient(buffer: ByteBuffer, client: SocketChannel, message: ByteBuffer): IO[Exception, ByteBuffer] =
    for {
      _ <- buffer.flip
      _ <- client.write(message)
      _ <- buffer.clear
      _ <- client.close
    } yield message
}
