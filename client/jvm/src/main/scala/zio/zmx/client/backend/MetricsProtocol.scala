package zio.zmx.client.backend

import zio.stream.{ UStream, ZStream }
import zio.zmx.client.MetricsMessage
import zio.zmx.internal.{ MetricKey, MetricListener }
import zio.zmx.state.MetricState
import zio._
import java.time.Instant

trait MetricsProtocol {
  val statsStream: UStream[MetricsMessage]
}

object MetricsProtocol {
  def live: ZLayer[Any, Nothing, Has[MetricsProtocol]] = {
    for {
      hub     <- Hub.sliding[MetricsMessage](4096).toManaged_
      listener = hubListener(hub)
      _       <- ZManaged.makeEffectTotal_(zmx.internal.installListener(listener))(zmx.internal.removeListener(listener))
    } yield new MetricsProtocol {
      override val statsStream: UStream[MetricsMessage] =
        ZStream.fromHub(hub)
    }
  }.toLayer

  // Accessors

  val statsStream: ZStream[Has[MetricsProtocol], Nothing, MetricsMessage] =
    ZStream.accessStream[Has[MetricsProtocol]](_.get.statsStream)

  private def hubListener(hub: Hub[MetricsMessage]): MetricListener = new MetricListener {
    override def gaugeChanged(key: MetricKey.Gauge, value: Double, delta: Double): Unit =
      Runtime.default.unsafeRunAsync_(hub.publish(MetricsMessage.GaugeChange(key, Instant.now(), value, delta)))

    override def counterChanged(key: MetricKey.Counter, absValue: Double, delta: Double): Unit =
      Runtime.default.unsafeRunAsync_(hub.publish(MetricsMessage.CounterChange(key, Instant.now(), absValue, delta)))

    override def histogramChanged(key: MetricKey.Histogram, value: MetricState): Unit =
      Runtime.default.unsafeRunAsync_(hub.publish(MetricsMessage.HistogramChange(key, Instant.now(), value)))

    override def summaryChanged(key: MetricKey.Summary, value: MetricState): Unit =
      Runtime.default.unsafeRunAsync_(hub.publish(MetricsMessage.SummaryChange(key, Instant.now(), value)))

    override def setChanged(key: MetricKey.SetCount, value: MetricState): Unit =
      Runtime.default.unsafeRunAsync_(hub.publish(MetricsMessage.SetChange(key, Instant.now(), value)))

  }

}
