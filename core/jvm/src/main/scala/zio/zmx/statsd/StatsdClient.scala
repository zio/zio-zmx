package zio.zmx.statsd

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

import scala.util.Try

import zio._
import zio.metrics._

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
      Try { channel.write(ByteBuffer.wrap(ab)).toLong }.getOrElse(0L)
  }

  private def channelM(host: String, port: Int): ZIO[Scope, Throwable, DatagramChannel] =
    ZIO.fromAutoCloseable(ZIO.attempt {
      val channel = DatagramChannel.open()
      channel.connect(new InetSocketAddress(host, port))
      channel
    })

  val live: ZLayer[StatsdConfig, Nothing, StatsdClient] =
    ZLayer.scoped {
      for {
        config  <- ZIO.service[StatsdConfig]
        channel <- channelM(config.host, config.port).orDie
        client   = new Live(channel)
        listener = new StatsdListener(client) {}
        _        = MetricClient.unsafeInstallListener(listener)
      } yield client
    }

  val default: ZLayer[Any, Nothing, StatsdClient] =
    ZLayer.succeed(StatsdConfig.default) >>> live

}
