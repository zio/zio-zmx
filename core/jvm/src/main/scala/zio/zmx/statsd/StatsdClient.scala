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
  private[statsd] def close(): Unit
}

object StatsdClient {

  private class Live(channel: DatagramChannel) extends StatsdClient {

    def write(chunk: Chunk[Byte]): Long =
      write(chunk.toArray)

    def write(s: String): Long = write(s.getBytes())

    private[statsd] def close(): Unit =
      try channel.close()
      catch { case _: Throwable => () }

    private def write(ab: Array[Byte]): Long =
      Try(channel.write(ByteBuffer.wrap(ab)).toLong).getOrElse(0L)
  }

  private def channelM(host: String, port: Int) =
    ZIO
      .attempt {
        val channel = DatagramChannel.open()
        channel.connect(new InetSocketAddress(host, port))
        channel
      }
      .tap(_ => ZIO.logInfo(s"Connected to UDP <$host:$port>"))

  def withStatsd[R, E, A](zio: ZIO[R, E, A]) = {

    val acquire: ZIO[R, Nothing, (StatsdClient, MetricListener)] = channelM("localhost", 8125).map { c =>
      val client: StatsdClient     = new Live(c)
      val listener: MetricListener = StatsdListener.make(client)
      MetricClient.unsafeInstallListener(listener)
      (client, listener)
    }.orDie

    val release: ((StatsdClient, MetricListener)) => URIO[R, Any] = p =>
      ZIO.succeed {
        MetricClient.unsafeRemoveListener(p._2)
        p._1.close()
      }

    val use: ((StatsdClient, MetricListener)) => ZIO[R, E, A] = p =>
      for {
        _       <- ZIO.logInfo(s"Registering StatsdListener")
        listener = StatsdListener.make(p._1)
        _        = MetricClient.unsafeInstallListener(p._2)
        res     <- zio
      } yield res

    ZIO.acquireReleaseWith[R, E, (StatsdClient, MetricListener), A](
      acquire,
      release,
      use,
    )
  }
}
