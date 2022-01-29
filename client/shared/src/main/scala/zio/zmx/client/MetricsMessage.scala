package zio.zmx.client

import upickle.default._
import zio._
import zio.metrics._

import java.time.Instant

object UPickleCoreImplicits {

  implicit val rwInstant: ReadWriter[Instant] =
    readwriter[Long].bimap(_.toEpochMilli(), Instant.ofEpochMilli)

  implicit val rwDuration: ReadWriter[Duration] =
    readwriter[Long].bimap(_.toMillis(), Duration.fromMillis)

  implicit def rwChunk[A](implicit
    rwA: ReadWriter[A]
  ): ReadWriter[Chunk[A]] =
    readwriter[List[A]].bimap(_.toList, Chunk.fromIterable)

  implicit lazy val rwMetricLabel: ReadWriter[MetricLabel] = macroRW

  implicit lazy val rwMetricKey: ReadWriter[MetricKey]              = macroRW
  implicit lazy val rwGaugeKey: ReadWriter[MetricKey.Gauge]         = macroRW
  implicit lazy val rwHistogramKey: ReadWriter[MetricKey.Histogram] = macroRW
  implicit lazy val rwCounterKey: ReadWriter[MetricKey.Counter]     = macroRW
  implicit lazy val rwSummaryKey: ReadWriter[MetricKey.Summary]     = macroRW
  implicit lazy val rwSetCountKey: ReadWriter[MetricKey.SetCount]   = macroRW

  implicit lazy val rwMetricTypeCounter: ReadWriter[MetricType.Counter]           = macroRW
  implicit lazy val rwMetricTypeGauge: ReadWriter[MetricType.Gauge]               = macroRW
  implicit lazy val rwMetricTypeHistogram: ReadWriter[MetricType.DoubleHistogram] = macroRW
  implicit lazy val rwMetricTypeSummary: ReadWriter[MetricType.Summary]           = macroRW
  implicit lazy val rwMetricTypeSetCount: ReadWriter[MetricType.SetCount]         = macroRW

  implicit lazy val rwMetricState: ReadWriter[MetricState]                   = macroRW
  implicit lazy val rwMetricType: ReadWriter[MetricType]                     = macroRW
  implicit lazy val rwBoundaries: ReadWriter[ZIOMetric.Histogram.Boundaries] = macroRW
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

  implicit lazy val rwClientMessage: ReadWriter[ClientMessage]           = macroRW
  implicit lazy val rwDisconnect: ReadWriter[Disconnect]                 = macroRW
  implicit lazy val rwConnected: ReadWriter[Connected]                   = macroRW
  implicit lazy val rwSubscribe: ReadWriter[Subscription]                = macroRW
  implicit lazy val rwRemoveSubscription: ReadWriter[RemoveSubscription] = macroRW
  implicit lazy val rwNotification: ReadWriter[MetricsNotification]      = macroRW
  implicit lazy val rwAvailableMetrics: ReadWriter[AvailableMetrics]     = macroRW
}
