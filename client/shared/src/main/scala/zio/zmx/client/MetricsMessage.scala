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

  implicit val encHistogram: JsonEncoder[MetricKeyType.Histogram] =
    DeriveJsonEncoder.gen[MetricKeyType.Histogram]
  implicit val decHistogram: JsonDecoder[MetricKeyType.Histogram] =
    DeriveJsonDecoder.gen[MetricKeyType.Histogram]

  implicit val encSummary: JsonEncoder[MetricKeyType.Summary] =
    DeriveJsonEncoder.gen[MetricKeyType.Summary]
  implicit val decSummary: JsonDecoder[MetricKeyType.Summary] =
    DeriveJsonDecoder.gen[MetricKeyType.Summary]

  implicit val encMetricLabel: JsonEncoder[MetricLabel] =
    DeriveJsonEncoder.gen[MetricLabel]
  implicit val decMetricLabel: JsonDecoder[MetricLabel] =
    DeriveJsonDecoder.gen[MetricLabel]

  sealed trait KeyTypes {
    val name: String
  }

  object KeyTypes {
    case object Counter   extends KeyTypes { override val name: String = "Counter"   }
    case object Gauge     extends KeyTypes { override val name: String = "Gauge"     }
    case object Frequency extends KeyTypes { override val name: String = "Frequency" }
    case object Histogram extends KeyTypes { override val name: String = "Histogram" }
    case object Summary   extends KeyTypes { override val name: String = "Summary"   }
  }

  // We map a metric key as a tuple:
  //  - The name of the key
  //  - The tags of the key
  //  - The metric Key as a String
  //  - The Json encoded String details

  implicit val encMetricKey: JsonEncoder[MetricKey[Any]] =
    JsonEncoder[(String, Set[MetricLabel], String, String)].contramap[MetricKey[Any]] { key =>
      key.keyType match {
        case MetricKeyType.Counter       =>
          (key.name, key.tags, KeyTypes.Counter.name, "{}")
        case MetricKeyType.Gauge         =>
          (key.name, key.tags, KeyTypes.Gauge.name, "{}")
        case MetricKeyType.Frequency     =>
          (key.name, key.tags, KeyTypes.Frequency.name, "{}")
        case hk: MetricKeyType.Histogram =>
          (key.name, key.tags, KeyTypes.Histogram.name, hk.toJson)
        case sk: MetricKeyType.Summary   =>
          (key.name, key.tags, KeyTypes.Summary.name, sk.toJson)
        case _                           => (key.name, key.tags, "Untyped", "{}")
      }
    }

  implicit val decMetricKey: JsonDecoder[MetricKey[_]] = {
    import KeyTypes._

    JsonDecoder[(String, Set[MetricLabel], String, String)].mapOrFail { case (name, tags, keyType, details) =>
      keyType match {
        case Counter.name   => Right(MetricKey.counter(name).tagged(tags))
        case Gauge.name     => Right(MetricKey.gauge(name).tagged(tags))
        case Frequency.name => Right(MetricKey.frequency(name).tagged(tags))
        case Histogram.name =>
          details.fromJson[MetricKeyType.Histogram].map(hk => MetricKey.histogram(name, hk.boundaries).tagged(tags))
        case Summary.name   =>
          details
            .fromJson[MetricKeyType.Summary]
            .map(sk => MetricKey.summary(name, sk.maxAge, sk.maxSize, sk.error, sk.quantiles).tagged(tags))
        case _              => Left(s"Could not instantiate MetricKey for KeyType <$keyType>")
      }
    }
  }

  // implicit val encPair: JsonEncoder[MetricKey.Untyped] = ???
  // implicit val decPair: JsonDecoder[MetricKey.Untyped] = ???

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
  case object Connect                                               extends ClientMessage
  final case class Connected(cltId: String)                         extends ClientMessage
  final case class Disconnect(cltId: String)                        extends ClientMessage
  final case class Subscription(clt: String, id: String, keys: Seq[MetricKey.Untyped], interval: Duration)
      extends ClientMessage
  final case class RemoveSubscription(clt: String, id: String)      extends ClientMessage
  final case class MetricsNotification(cltId: String, subId: String, when: Instant, states: Set[MetricPair.Untyped])
      extends ClientMessage
  final case class AvailableMetrics(keys: Chunk[MetricKey.Untyped]) extends ClientMessage

  implicit lazy val encClientMessage: JsonEncoder[ClientMessage] = ???
  implicit lazy val decClientMessage: JsonDecoder[ClientMessage] = ???

  // implicit lazy val rwClientMessage: ReadWriter[ClientMessage]           = macroRW
  // implicit lazy val rwDisconnect: ReadWriter[Disconnect]                 = macroRW
  // implicit lazy val rwConnected: ReadWriter[Connected]                   = macroRW
  // implicit lazy val rwSubscribe: ReadWriter[Subscription]                = macroRW
  // implicit lazy val rwRemoveSubscription: ReadWriter[RemoveSubscription] = macroRW
  // implicit lazy val rwNotification: ReadWriter[MetricsNotification]      = macroRW
  // implicit lazy val rwAvailableMetrics: ReadWriter[AvailableMetrics]     = macroRW
}
