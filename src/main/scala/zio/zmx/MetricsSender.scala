package zio.zmx

import zio.zmx.metrics.UDPClient
import zio.zmx.metrics.UDPClient.UDPClient
import zio.{Chunk, TaskLayer, UIO, ZIO, ZLayer}

object MetricsSender {

  trait Service[B] {
    def send(b: B): UIO[Unit]
  }

  // thin adapter to the UDPClient service
  def udpClientMetricsSender(config: MetricsConfig): TaskLayer[MetricsSender[Chunk[Byte]]] =
    ZLayer.fromEffect {
      (for {
        udpClient <- ZIO.access[UDPClient](_.get)
      } yield new Service[Chunk[Byte]] {
        // TODO: What to do with the exceptions returned by udpClient.write()
        override def send(b: Chunk[Byte]): UIO[Unit] = udpClient.write(b).either.as(())
      }).provideLayer(UDPClient.live(config))
    }
}
