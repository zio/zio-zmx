package zio.zmx.statsd

import zio._

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import zio.zmx.MetricsClient
import zio.zmx.MetricSnapshot.Json
import scala.util.Try

trait StatsdClient extends MetricsClient {

  def snapshot: ZIO[Any, Nothing, Json]

  private[zmx] def write(s: String): Long
  private[zmx] def write(chunk: Chunk[Byte]): Long
}

object StatsdClient {

  private class Live(channel: DatagramChannel) extends StatsdClient {

    def snapshot: ZIO[Any, Nothing, Json] =
      ZIO.succeed(zmx.encode.JsonEncoder.encode(zmx.internal.snapshot().values))

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

  val live: ZLayer[Has[StatsdConfig], Nothing, Has[StatsdClient]] =
    ZLayer.fromManaged {
      for {
        config  <- ZManaged.service[StatsdConfig]
        channel <- channelM(config.host, config.port).orDie
        client   = new Live(channel)
        listener = new StatsdListener(client) {}
        _       <- ZManaged.effectTotal(zmx.internal.installListener(listener))
      } yield client
    }

  val default: ZLayer[Any, Nothing, Has[StatsdClient]] =
    ZLayer.succeed(StatsdConfig.default) >>> live

  val snapshot: ZIO[Has[StatsdClient], Nothing, Json] =
    ZIO.serviceWith(_.snapshot)
}
