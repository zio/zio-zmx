package zio.zmx.metrics

import java.nio.channels.DatagramChannel
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class UDPClientUnsafe(channel: DatagramChannel) {
  def send(data: String): Int = {
    val buf: ByteBuffer = ByteBuffer.allocate(512)
    buf.clear()
    buf.put(data.getBytes())
    buf.flip()

    channel.write(buf)
  }
}
object UDPClientUnsafe {
  def apply(host: String, port: Int): UDPClientUnsafe = {
    val address = new InetSocketAddress(host, port)
    new UDPClientUnsafe(DatagramChannel.open().connect(address))
  }
}
