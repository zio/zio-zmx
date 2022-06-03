package zio.metrics.connectors.statsd

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import zio._

trait StatsdClient {
  private[connectors] def send(chunk: Chunk[Byte]): Long
}

private[statsd] object StatsdClient {

  private class Live(channel: DatagramChannel) extends StatsdClient {

    override def send(chunk: Chunk[Byte]): Long =
      write(chunk.toArray)

    private def write(ab: Array[Byte]): Long =
      Try(channel.write(ByteBuffer.wrap(ab)).toLong) match {
        case Success(value) =>
          // println(s"Sent UDP data [$value]")
          value
        case Failure(_)     =>
          // t.printStackTrace()
          0L
      }

  }

  private def channelZIO(host: String, port: Int): ZIO[Scope, Throwable, DatagramChannel] =
    ZIO.fromAutoCloseable(ZIO.attempt {
      val channel = DatagramChannel.open()
      channel.connect(new InetSocketAddress(host, port))
      channel
    })

  private[connectors] def make: ZIO[Scope & StatsdConfig, Nothing, StatsdClient] =
    for {
      config  <- ZIO.service[StatsdConfig]
      channel <- channelZIO(config.host, config.port).orDie
      client   = new Live(channel)
    } yield client

}
