package zio.metrics.connectors.statsd

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

import scala.util.Try

import zio._
import zio.metrics.connectors._

final case class StatsdPublisher private (client: StatsdClient) extends MetricPublisher[String] {

  override def publish(metrics: Iterable[String]): UIO[MetricPublisher.Result] =
    (ZIO
      .attempt(client.write(metrics.mkString("\n")))
      .as(MetricPublisher.Result.Success))
      .catchAll(t => ZIO.succeed(MetricPublisher.Result.TransientFailure(t)))
}

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
      Try(channel.write(ByteBuffer.wrap(ab)).toLong).getOrElse(0L)
  }

  private def channelZIO(host: String, port: Int): ZIO[Scope, Throwable, DatagramChannel] =
    ZIO.fromAutoCloseable(ZIO.attempt {
      val channel = DatagramChannel.open()
      channel.connect(new InetSocketAddress(host, port))
      channel
    })

  val live: ZLayer[StatsdConfig, Nothing, StatsdPublisher] =
    ZLayer.scoped {
      for {
        config  <- ZIO.service[StatsdConfig]
        channel <- channelZIO(config.host, config.port).orDie
        client   = new Live(channel)
      } yield StatsdPublisher(client)
    }

  val default: ZLayer[Any, Nothing, StatsdPublisher] =
    ZLayer.succeed(StatsdConfig.default) >>> live

}
