package zio.zmx.statsd

import zio._
import zio.metrics._

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import scala.util.Try

trait StatsdClient {

  private[statsd] def write(s: String): Long
  private[statsd] def write(chunk: Chunk[Byte]): Long
}

object StatsdClient {

  private class Live(channel: DatagramChannel) extends StatsdClient {

    def write(chunk: Chunk[Byte]): Long =
      write(chunk.toArray)

    def write(s: String): Long = write(s.getBytes())

    private def write(ab: Array[Byte]): Long =
      (Try { channel.write(ByteBuffer.wrap(ab)).toLong }).getOrElse(0L)
  }

  private def channelM(host: String, port: Int): ZManaged[Any, Throwable, DatagramChannel] =
    ZManaged.fromAutoCloseable(Task {
      val channel = DatagramChannel.open()
      channel.connect(new InetSocketAddress(host, port))
      channel
    })

  val live: ZServiceBuilder[Has[StatsdConfig], Nothing, Has[StatsdClient]] =
    ZServiceBuilder.fromManaged {
      for {
        config  <- ZManaged.service[StatsdConfig]
        channel <- channelM(config.host, config.port).orDie
        client   = new Live(channel)
        listener = new StatsdListener(client) {}
        _        = MetricClient.unsafeInstallListener(listener)
      } yield client
    }

  val default: ZServiceBuilder[Any, Nothing, Has[StatsdClient]] =
    ZServiceBuilder.succeed(StatsdConfig.default) >>> live

}
