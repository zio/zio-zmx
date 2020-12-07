package zio.zmx.diagnostics.nio

import java.io.IOException
import java.nio.channels.{SelectionKey => JSelectionKey, SocketChannel => JSocketChannel}
import java.nio.{ ByteBuffer => JByteBuffer }

import zio.IO

class SocketChannel(val channel: JSocketChannel) {

  def configureBlocking(block: Boolean): IO[IOException, Unit] =
    IO.effect(channel.configureBlocking(block)).unit.refineToOrDie[IOException]

  def register(sel: Selector, op: Integer): IO[IOException, SelectionKey] =
    IO.effect(new SelectionKey(channel.register(sel.selector, op)))
      .refineToOrDie[IOException]

  val close: IO[Exception, Unit] =
    IO.effect(channel.close()).refineToOrDie[Exception]

  def read(b: ByteBuffer): IO[IOException, Int] =
    IO.effect(channel.read(b.buffer.asInstanceOf[JByteBuffer])).refineToOrDie[IOException]

  def write(b: ByteBuffer): IO[Exception, Int] =
    IO.effect(channel.write(b.buffer.asInstanceOf[JByteBuffer])).refineToOrDie[IOException]
}

object SocketChannel {

  val OpRead: Int = JSelectionKey.OP_READ
}




