package zio.zmx.statsd

import zio._

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

object StatsdClient {

  type StatsdClient = Has[StatsdClientSvc]

  trait StatsdClientSvc {

    def write(chunk: Chunk[Byte]): Task[Long]

  }

  private class Live(channel: DatagramChannel) extends StatsdClientSvc {

    override def write(chunk: Chunk[Byte]): Task[Long] =
      Task(channel.write(ByteBuffer.wrap(chunk.toArray)).toLong)

  }

  private def channelM(host: String, port: Int): ZManaged[Any, Throwable, DatagramChannel] =
    ZManaged.fromAutoCloseable(Task {
      val channel = DatagramChannel.open()
      channel.connect(new InetSocketAddress(host, port))
      channel
    })

  def live(config: StatsdConfig): TaskLayer[StatsdClient] =
    ZLayer.fromManaged(channelM(config.host, config.port).map(ch => new Live(ch)))

}
