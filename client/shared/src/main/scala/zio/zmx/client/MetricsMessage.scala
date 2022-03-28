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

  // We map a metric key as a tuple:
  //  - The name of the key
  //  - The tags of the key
  //  - The metric Key as a String
  //  - The Json encoded Key details if needed

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
        // This should not happen at all
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

  sealed private trait KeyTypes {
    val name: String
  }

  private object KeyTypes {
    case object Counter   extends KeyTypes { override val name: String = "Counter"   }
    case object Gauge     extends KeyTypes { override val name: String = "Gauge"     }
    case object Frequency extends KeyTypes { override val name: String = "Frequency" }
    case object Histogram extends KeyTypes { override val name: String = "Histogram" }
    case object Summary   extends KeyTypes { override val name: String = "Summary"   }
  }
}

sealed trait ClientMessage

object ClientMessage {

  import MetricsMessageImplicits._

  /**
   * A message from a client to the server to initialize a new connection
   */
  case object Connect extends ClientMessage

  /**
   * The response from the server to the client for a successful Connection.
   *
   * @param cltId The connection id for the new connection
   */
  final case class Connected(cltId: String) extends ClientMessage

  /**
   * A message from the client to the server to close an existing connection
   *
   * @param cltId The id of the connection to be closed
   */
  final case class Disconnect(cltId: String) extends ClientMessage

  /**
   * A message from a formerly connected client to create or replace a subscription with a given id
   */
  final case class Subscription(clt: String, id: String, keys: Set[MetricKey[Any]], interval: Duration)
      extends ClientMessage

  /**
   * A message sent from the client to remove a specific subscription
   */
  final case class RemoveSubscription(clt: String, id: String) extends ClientMessage

  /**
   * A message sent from the server a an update for a specific subscription
   */
  final case class MetricsNotification(cltId: String, subId: String, when: Instant, states: Set[MetricPair.Untyped])
      extends ClientMessage

  /**
   * A message sent by the server to announce the metrics currently available
   */
  final case class AvailableMetrics(keys: Set[MetricKey[Any]]) extends ClientMessage

  implicit lazy val encNotification: JsonEncoder[MetricPair.Untyped] =
    JsonEncoder[(MetricKey[Any], MetricState[_])].contramap[MetricPair.Untyped] { pair =>
      (pair.metricKey.asInstanceOf[MetricKey[Any]], pair.metricState)
    }

  implicit lazy val decNotification: JsonDecoder[MetricPair.Untyped] =
    JsonDecoder[(MetricKey[Any], MetricState[_])].map { case (key, state) =>
      MetricPair(key.asInstanceOf[MetricKey[MetricKeyType { type Out = Any }]], state.asInstanceOf[MetricState[Any]])
    }

  implicit lazy val encClientMessage: JsonEncoder[ClientMessage] = DeriveJsonEncoder.gen[ClientMessage]
  implicit lazy val decClientMessage: JsonDecoder[ClientMessage] = DeriveJsonDecoder.gen[ClientMessage]
}
