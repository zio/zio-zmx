package zio.zmx.metrics

import zio.zmx.JavaNioUtils.ZDatagramChannel

import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel
import zio.{ ZIO, ZManaged }

object UDPClient {

  val clientM: ZManaged[Any, Throwable, ZDatagramChannel] =
    clientM("localhost", 8125)

  def clientM(host: String, port: Int): ZManaged[Any, Throwable, ZDatagramChannel] = {
    val client = ZIO.effect {
      val channel = DatagramChannel.open()
      channel.connect(new InetSocketAddress(host, port))
      channel
    }

    ZManaged.fromAutoCloseable(client).map(ZDatagramChannel.apply)
  }

}
