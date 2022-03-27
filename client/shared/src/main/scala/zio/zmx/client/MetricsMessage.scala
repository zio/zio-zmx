package zio.zmx.client

import zio._
import zio.json._
import zio.metrics._

import java.time.Instant

object MetricsMessageImplicits {

  implicit val encInstant: JsonEncoder[Instant] =
    JsonEncoder[Long].contramap(_.toEpochMilli)
  implicit val decInstant: JsonDecoder[Instant] =
    JsonDecoder[Long].map(Instant.ofEpochMilli)

  implicit val encDuration: JsonEncoder[Duration] =
    JsonEncoder[Long].contramap(_.toMillis)
  implicit val decDuration: JsonDecoder[Duration] =
    JsonDecoder[Long].map(Duration.fromMillis)

  implicit val encMetricState: JsonEncoder[MetricState[_]] =
    DeriveJsonEncoder.gen[MetricState[_]]
  implicit val decMetricState: JsonDecoder[MetricState[_]] =
    DeriveJsonDecoder.gen[MetricState[_]]

  implicit val encHistogramBoundaries: JsonEncoder[MetricKeyType.Histogram.Boundaries] =
    DeriveJsonEncoder.gen[MetricKeyType.Histogram.Boundaries]
  implicit val decHistogramBoundaries: JsonDecoder[MetricKeyType.Histogram.Boundaries] =
    DeriveJsonDecoder.gen[MetricKeyType.Histogram.Boundaries]

  implicit val encMetricKeyType: JsonEncoder[MetricKeyType] =
    DeriveJsonEncoder.gen[MetricKeyType]
  implicit val decMetricKeyType: JsonDecoder[MetricKeyType] =
    DeriveJsonDecoder.gen[MetricKeyType]

  implicit val encMetricLabel: JsonEncoder[MetricLabel] =
    DeriveJsonEncoder.gen[MetricLabel]
  implicit val decMetricLabel: JsonDecoder[MetricLabel] =
    DeriveJsonDecoder.gen[MetricLabel]

  implicit val encMetricKey: JsonEncoder[MetricKey[MetricKeyType]] =
    JsonEncoder[(String, MetricKeyType, Set[MetricLabel])].contramap(k => (k.name, k.keyType, k.labels))

  implicit val decMetricKey: JsonDecoder[MetricKey[MetricKeyType]] =
    JsonDecoder[(String, MetricKeyType, Set[MetricLabel])].map(
      { case (name, keyType, labels) => MetricKey(name, keyType, labels) }
    )

  implicit val encPair: JsonEncoder[MetricKey.Untyped] = ???
  implicit val decPair: JsonDecoder[MetricKey.Untyped] = ???

  // implicit lazy val rwMetricLabel: ReadWriter[MetricLabel] = macroRW

  // implicit lazy val rwMetricKey: ReadWriter[MetricKey]        = macroRW
  // implicit lazy val encGaugeKey: JsonEncoder[MetricKey.Gauge] =
  //   DeriveJsonEncoder.gen[MetricKey.Gauge]

  // implicit lazy val rwHistogramKey: ReadWriter[MetricKey.Histogram] = macroRW
  // implicit lazy val rwCounterKey: ReadWriter[MetricKey.Counter]     = macroRW
  // implicit lazy val rwSummaryKey: ReadWriter[MetricKey.Summary]     = macroRW
  // implicit lazy val rwSetCountKey: ReadWriter[MetricKey.SetCount]   = macroRW

  // implicit lazy val rwMetricTypeCounter: ReadWriter[MetricType.Counter]           = macroRW
  // implicit lazy val rwMetricTypeGauge: ReadWriter[MetricType.Gauge]               = macroRW
  // implicit lazy val rwMetricTypeHistogram: ReadWriter[MetricType.DoubleHistogram] = macroRW
  // implicit lazy val rwMetricTypeSummary: ReadWriter[MetricType.Summary]           = macroRW
  // implicit lazy val rwMetricTypeSetCount: ReadWriter[MetricType.SetCount]         = macroRW

  // implicit lazy val rwMetricState: ReadWriter[MetricState]                   = macroRW
  // implicit lazy val rwMetricType: ReadWriter[MetricType]                     = macroRW
  // implicit lazy val rwBoundaries: ReadWriter[ZIOMetric.Histogram.Boundaries] = macroRW
}

sealed trait ClientMessage

object ClientMessage {
  case object Connect                                                    extends ClientMessage
  final case class Connected(cltId: String)                              extends ClientMessage
  final case class Disconnect(cltId: String)                             extends ClientMessage
  final case class Subscription(clt: String, id: String, keys: Seq[MetricKey.Untyped], interval: Duration)
      extends ClientMessage
  final case class RemoveSubscription(clt: String, id: String)           extends ClientMessage
  final case class MetricsNotification(cltId: String, subId: String, when: Instant, states: Set[MetricPair.Untyped])
      extends ClientMessage
  final case class AvailableMetrics(keys: Set[MetricKey[MetricKeyType]]) extends ClientMessage

  import MetricsMessageImplicits._

  implicit lazy val encClientMessage: JsonEncoder[ClientMessage] = DeriveJsonEncoder.gen[ClientMessage]
  // implicit lazy val decClientMessage: JsonDecoder[ClientMessage] = DeriveJsonDecoder.gen[ClientMessage]
  // implicit lazy val rwClientMessage: ReadWriter[ClientMessage]           = macroRW
  // implicit lazy val rwDisconnect: ReadWriter[Disconnect]                 = macroRW
  // implicit lazy val rwConnected: ReadWriter[Connected]                   = macroRW
  // implicit lazy val rwSubscribe: ReadWriter[Subscription]                = macroRW
  // implicit lazy val rwRemoveSubscription: ReadWriter[RemoveSubscription] = macroRW
  // implicit lazy val rwNotification: ReadWriter[MetricsNotification]      = macroRW
  // implicit lazy val rwAvailableMetrics: ReadWriter[AvailableMetrics]     = macroRW
}
