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
  val live: ZLayer[Any, Nothing, MetricsProtocol] = {
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

  val statsStream: ZStream[MetricsProtocol, Nothing, MetricsMessage] =
    ZStream.environmentWithStream[MetricsProtocol](_.get.statsStream)

  private def hubListener(hub: Hub[MetricsMessage]): MetricListener = new MetricListener { self =>
    private def publish(msg: MetricsMessage): Unit = {
      MetricClient.unsafeRemoveListener(self)
      Runtime.default.unsafeRunAsync(hub.publish(msg))
      MetricClient.unsafeInstallListener(self)
    }

    override def unsafeGaugeObserved(key: MetricKey.Gauge, value: Double, delta: Double): Unit =
      publish(MetricsMessage.GaugeChange(key, Instant.now(), value, delta))

    override def unsafeCounterObserved(key: MetricKey.Counter, absValue: Double, delta: Double): Unit =
      publish(MetricsMessage.CounterChange(key, Instant.now(), absValue, delta))

    override def unsafeHistogramObserved(key: MetricKey.Histogram, value: Double): Unit =
      MetricClient.unsafeState(key) match {
        case Some(value) => publish(MetricsMessage.HistogramChange(key, Instant.now(), value))
        case _           =>
      }

    override def unsafeSummaryObserved(key: MetricKey.Summary, value: Double): Unit =
      MetricClient.unsafeState(key) match {
        case Some(value) => publish(MetricsMessage.SummaryChange(key, Instant.now(), value))
        case _           =>
      }

    override def unsafeSetObserved(key: MetricKey.SetCount, value: String): Unit =
      MetricClient.unsafeState(key) match {
        case Some(value) => publish(MetricsMessage.SetChange(key, Instant.now(), value))
        case _           =>
      }

  }

}
