package zio.zmx.client.backend

import zio.stream.{ UStream, ZStream }
import zio.zmx.client.MetricsMessage
import zio._
import zio.metrics._
import java.time.Instant

trait MetricsProtocol {
  val statsStream: UStream[MetricsMessage]
}

object MetricsProtocol {
  val live: ZLayer[Any, Nothing, Has[MetricsProtocol]] = {
    for {
      hub     <- Hub.sliding[MetricsMessage](4096).toManaged
      listener = hubListener(hub)
      _       <- ZManaged.acquireReleaseSucceed {
                   MetricClient.unsafeInstallListener(listener)
                   println("listener installed")
                 } {
                   MetricClient.unsafeRemoveListener(listener)
                   println("listener removed")
                 }
    } yield new MetricsProtocol {
      override val statsStream: UStream[MetricsMessage] =
        ZStream.fromHub(hub)
    }
  }.toLayer

  // Accessors

  val statsStream: ZStream[Has[MetricsProtocol], Nothing, MetricsMessage] =
    ZStream.accessStream[Has[MetricsProtocol]](_.get.statsStream)

  private def hubListener(hub: Hub[MetricsMessage]): MetricListener = new MetricListener {

    private def publish(msg: MetricsMessage): Unit =
      Runtime.default.unsafeRunAsync(hub.publish(msg))

    override def unsafeGaugeChanged(key: MetricKey.Gauge, value: Double, delta: Double): Unit =
      publish(MetricsMessage.GaugeChange(key, Instant.now(), value, delta))

    override def unsafeCounterChanged(key: MetricKey.Counter, absValue: Double, delta: Double): Unit =
      publish(MetricsMessage.CounterChange(key, Instant.now(), absValue, delta))

    override def unsafeHistogramChanged(key: MetricKey.Histogram, value: MetricState): Unit =
      publish(MetricsMessage.HistogramChange(key, Instant.now(), value))

    override def unsafeSummaryChanged(key: MetricKey.Summary, value: MetricState): Unit =
      publish(MetricsMessage.SummaryChange(key, Instant.now(), value))

    override def unsafeSetChanged(key: MetricKey.SetCount, value: MetricState): Unit =
      publish(MetricsMessage.SetChange(key, Instant.now(), value))

  }

}
