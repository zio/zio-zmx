package zio.zmx.client

import zio._
import zio.metrics._
import java.time.Instant

import upickle.default._

object UPickleCoreImplicits {
  implicit val rwInstant: ReadWriter[Instant]   =
    readwriter[Long].bimap(_.toEpochMilli(), Instant.ofEpochMilli(_))
  implicit val rwDuration: ReadWriter[Duration] =
    readwriter[Long].bimap(_.toMillis(), Duration.fromMillis(_))

  implicit lazy val rwMetricLabel: ReadWriter[MetricLabel] = macroRW[MetricLabel]

  implicit lazy val rwMetricKey: ReadWriter[MetricKey]              = macroRW[MetricKey]
  implicit lazy val rwGaugeKey: ReadWriter[MetricKey.Gauge]         = macroRW[MetricKey.Gauge]
  implicit lazy val rwHistogramKey: ReadWriter[MetricKey.Histogram] = macroRW[MetricKey.Histogram]
  implicit lazy val rwCounterKey: ReadWriter[MetricKey.Counter]     = macroRW[MetricKey.Counter]
  implicit lazy val rwSummaryKey: ReadWriter[MetricKey.Summary]     = macroRW[MetricKey.Summary]
  implicit lazy val rwSetCountKey: ReadWriter[MetricKey.SetCount]   = macroRW[MetricKey.SetCount]

  implicit lazy val rwMetricTypeCounter   = macroRW[MetricType.Counter]
  implicit lazy val rwMetricTypeGauge     = macroRW[MetricType.Gauge]
  implicit lazy val rwMetricTypeHistogram = macroRW[MetricType.DoubleHistogram]
  implicit lazy val rwMetricTypeSummary   = macroRW[MetricType.Summary]
  implicit lazy val rwMetricTypeSetCount  = macroRW[MetricType.SetCount]

  implicit lazy val rwMetricState: ReadWriter[MetricState]                   = macroRW[MetricState]
  implicit lazy val rwMetricType: ReadWriter[MetricType]                     = macroRW[MetricType]
  implicit lazy val rwBoundaries: ReadWriter[ZIOMetric.Histogram.Boundaries] = macroRW[ZIOMetric.Histogram.Boundaries]
}

sealed trait ClientMessage

object ClientMessage {
  case object Connect                                                                              extends ClientMessage
  final case class Connected(cltId: String)                                                        extends ClientMessage
  final case class Disconnect(cltId: String)                                                       extends ClientMessage
  final case class Subscription(clt: String, id: String, keys: Seq[MetricKey], interval: Duration) extends ClientMessage
  final case class RemoveSubscription(clt: String, id: String)                                     extends ClientMessage
  final case class MetricsNotification(cltId: String, subId: String, when: Instant, states: Map[MetricKey, MetricState])
      extends ClientMessage
  final case class AvailableMetrics(keys: Seq[MetricKey])                                          extends ClientMessage

  import UPickleCoreImplicits._

  implicit lazy val rwClientMessage: ReadWriter[ClientMessage]           = macroRW[ClientMessage]
  implicit lazy val rwDisconnect: ReadWriter[Disconnect]                 = macroRW[Disconnect]
  implicit lazy val rwConnected: ReadWriter[Connected]                   = macroRW[Connected]
  implicit lazy val rwSubscribe: ReadWriter[Subscription]                = macroRW[Subscription]
  implicit lazy val rwRemoveSubscription: ReadWriter[RemoveSubscription] = macroRW[RemoveSubscription]
  implicit lazy val rwNotification: ReadWriter[MetricsNotification]      = macroRW[MetricsNotification]
  implicit lazy val rwAvailableMetrics: ReadWriter[AvailableMetrics]     = macroRW[AvailableMetrics]
}
