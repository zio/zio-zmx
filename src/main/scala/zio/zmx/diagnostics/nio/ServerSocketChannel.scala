package zio.zmx.diagnostics.nio

import java.io.IOException
import java.nio.channels.{ServerSocketChannel => JServerSocketChannel}

import zio.{IO, UIO}

class ServerSocketChannel(val channel: JServerSocketChannel) {

  def bind(addr: InetSocketAddress): IO[IOException, Unit] =
    IO.effect(channel.bind(addr.address)).refineToOrDie[IOException].unit

  def configureBlocking(block: Boolean): IO[IOException, Unit] =
    IO.effect(channel.configureBlocking(block)).unit.refineToOrDie[IOException]

  val validOps: UIO[Integer] =
    IO.effectTotal(channel.validOps())

  def register(sel: Selector, op: Integer): IO[IOException, SelectionKey] =
    IO.effect(new SelectionKey(channel.register(sel.selector, op)))
      .refineToOrDie[IOException]

  val close: IO[Exception, Unit] =
    IO.effect(channel.close()).refineToOrDie[Exception]

  def accept: IO[IOException, SocketChannel] =
    IO.effect(channel.accept()).map(new SocketChannel(_)).refineToOrDie[IOException]
}

object ServerSocketChannel {

  final val open: IO[IOException, ServerSocketChannel] =
    IO.effect(new ServerSocketChannel(JServerSocketChannel.open())).refineToOrDie[IOException]
}
