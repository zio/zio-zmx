package zio.zmx

import zio.{ Chunk, Task }

import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

private[zmx] object JavaNioUtils {
  class ZDatagramChannel(private val datagramChannel: DatagramChannel) {
    def write(chk: Chunk[Byte]): Task[Long] = execute(_.write(ByteBuffer.wrap(chk.toArray)).toLong)

    def execute[B](f: DatagramChannel => B): Task[B] = Task(f(datagramChannel))
  }

  object ZDatagramChannel {
    def apply(datagramChannel: DatagramChannel): ZDatagramChannel = new ZDatagramChannel(datagramChannel)
  }
}
