package zio.zmx.metrics

import zio._
import zio.zmx._

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

private[zmx] object UDPClient {

  type UDPClient = Has[UDPClient.Service]

  trait Service {

    def write(chunk: Chunk[Byte]): Task[Long]

  }

  private class Live(channel: DatagramChannel) extends Service {

    override def write(chunk: Chunk[Byte]): Task[Long] =
      Task(channel.write(ByteBuffer.wrap(chunk.toArray)).toLong)

  }

  private def channelM: ZManaged[Any, Throwable, DatagramChannel] =
    channelM("localhost", 8125)

  private def channelM(host: String): ZManaged[Any, Throwable, DatagramChannel] =
    channelM(host, 8125)

  private def channelM(port: Int): ZManaged[Any, Throwable, DatagramChannel] =
    channelM("localhost", port)

  private def channelM(host: String, port: Int): ZManaged[Any, Throwable, DatagramChannel] =
    ZManaged.fromAutoCloseable(Task {
      val channel = DatagramChannel.open()
      channel.connect(new InetSocketAddress(host, port))
      channel
    })

  def live(config: MetricsConfig): TaskLayer[UDPClient] =
    ZLayer.fromManaged(
      for {
        channel <- (config.host, config.port) match {
                     case (None, None)       => channelM
                     case (Some(h), Some(p)) => channelM(h, p)
                     case (Some(h), None)    => channelM(h)
                     case (None, Some(p))    => channelM(p)
                   }
      } yield new Live(channel)
    )

}
