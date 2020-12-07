package zio.zmx.diagnostics.nio

import java.io.IOException
import java.nio.channels.{SelectionKey => JSelectionKey, SocketChannel => JSocketChannel}
import java.nio.{ ByteBuffer => JByteBuffer }

import zio.IO

class SocketChannel(val channel: JSocketChannel) {

  final def configureBlocking(block: Boolean): IO[IOException, Unit] =
    IO.effect(channel.configureBlocking(block)).unit.refineToOrDie[IOException]

  final def register(sel: Selector, op: Integer): IO[IOException, SelectionKey] =
    IO.effect(new SelectionKey(channel.register(sel.selector, op)))
      .refineToOrDie[IOException]

  final val close: IO[Exception, Unit] =
    IO.effect(channel.close()).refineToOrDie[Exception]

  final def read(b: ByteBuffer): IO[IOException, Int] =
    IO.effect(channel.read(b.buffer.asInstanceOf[JByteBuffer])).refineToOrDie[IOException]

  final def write(b: ByteBuffer): IO[Exception, Int] =
    IO.effect(channel.write(b.buffer.asInstanceOf[JByteBuffer])).refineToOrDie[IOException]
}

object SocketChannel {

  val OpRead = JSelectionKey.OP_READ
}




