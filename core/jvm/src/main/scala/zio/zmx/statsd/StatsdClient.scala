package zio.zmx.statsd

import zio._

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import zio.zmx.MetricsClient
import zio.zmx.MetricSnapshot.Json

// collapse statssclient and statslistener into a single file and don't expose write operators

trait StatsdClient extends MetricsClient {

  // don't expose these
  def write(s: String): Task[Long]
  def write(chunk: Chunk[Byte]): Task[Long]

}

object StatsdClient {

  private class Live(channel: DatagramChannel) extends StatsdClient {

    def snapshot: ZIO[Any, Nothing, Json] =
      ZIO.succeed(zmx.encode.JsonEncoder.encode(zmx.internal.snapshot().values))

    override def write(chunk: Chunk[Byte]): Task[Long] =
      write(chunk.toArray)

    def write(s: String): Task[Long] = write(s.getBytes())

    private def write(ab: Array[Byte]): Task[Long] =
      Task(channel.write(ByteBuffer.wrap(ab)).toLong)
  }

  private def channelM(host: String, port: Int): ZManaged[Any, Throwable, DatagramChannel] =
    ZManaged.fromAutoCloseable(Task {
      val channel = DatagramChannel.open()
      channel.connect(new InetSocketAddress(host, port))
      channel
    })

  val live: ZLayer[Has[StatsdConfig], Nothing, Has[Unit]] =
    ZLayer.fromManaged {
      for {
        config  <- ZManaged.service[StatsdConfig]
        channel <- channelM(config.host, config.port).orDie
        client   = new Live(channel)
        listener = new StatsdListener(client) {}
        _       <- ZManaged.effectTotal(zmx.internal.installListener(listener))
      } yield ()
    }

  val default: ZLayer[Any, Nothing, Has[Unit]] =
    ZLayer.succeed(StatsdConfig.default) >>> live

}
