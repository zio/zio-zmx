package zio.zmx.client

import java.time.Instant
import java.time.temporal.ChronoUnit

import zio._
import zio.json._
import zio.metrics._
import zio.test._
import zio.test.TestAspect._
import zio.zmx.client.MetricsMessageImplicits._

object MetricsMessageSpec extends ZIOSpecDefault {

  def spec = suite("For MetricsMessages")(
    serdeMetricsLabel,
    serdeDuration,
    serdeMetricKey,
    serdeClientMsg,
  ) @@ timed @@ parallel

  // a generator for Durations
  private val genDuration = Gen.long(100L, 1000L).map(l => Duration.fromMillis(l))

  private val genNonEmpty = Gen.alphaNumericStringBounded(1, 10)

  // Just generator Shortcut for MetricsLabels
  private val genLabel = genNonEmpty.zip(genNonEmpty).map { case (k, v) => MetricLabel(k, v) }

  // A generator for counter keys
  private val genKeyCounter: Gen[Sized, MetricKey[MetricKeyType.Counter]] =
    genNonEmpty.zip(genLabel).map { case (name, label) => MetricKey.counter(name).tagged(label) }

  // A generator for gauge keys
  private val genKeyGauge =
    genNonEmpty.zip(genLabel).map { case (name, label) => MetricKey.gauge(name).tagged(label) }

  // A generator for frequency keys
  private val genKeyFrequency =
    genNonEmpty.zip(genLabel).map { case (name, label) => MetricKey.frequency(name).tagged(label) }

  // A generator for histogram keys
  private val genKeyHistogram =
    genNonEmpty.zip(genLabel).map { case (name, label) =>
      MetricKey.histogram(name, MetricKeyType.Histogram.Boundaries.linear(0, 10, 11)).tagged(label)
    }

  // A generator for summary keys
  private val genKeySummary =
    genNonEmpty.zip(genLabel).map { case (name, label) =>
      MetricKey.summary(name, Duration.fromMillis(1000L), 100, 0.03d, Chunk(0.1, 0.5, 0.95)).tagged(label)
    }

  // A generator for MetricKeys
  private val genKey: Gen[Sized, MetricKey[Any]] =
    Gen.oneOf(genKeyCounter, genKeyGauge, genKeyFrequency, genKeyHistogram, genKeySummary)

  // A generator for Counter State
  private val genStateCounter: Gen[Any, MetricState[MetricKeyType.Counter]] =
    Gen.long(1L, 1000L).map(c => MetricState.Counter(c.toDouble))

  // A Generator for Gauge State
  private val genStateGauge: Gen[Any, MetricState[MetricKeyType.Gauge]] =
    Gen.double.map(c => MetricState.Gauge(c.toDouble))

  // A Generator for Frequency State
  // TODO: make this a Random generator
  private val genStateFrequency: Gen[Any, MetricState[MetricKeyType.Frequency]] =
    Gen.const(MetricState.Frequency(Map("foo" -> 1000L)))

  // A Generator for Summary State
  // TODO: make this a Random generator
  private val genStateSummary: Gen[Any, MetricState[MetricKeyType.Summary]] =
    Gen.const(MetricState.Summary(0.03, Chunk((0.1, Some(0.3)), (0.5, Some(0.3)), (0.95, Some(0.4))), 100L, 0.0, 1000.0, 2000L))

  // A Generator for Histogram State
  // TODO: make this a Random generator
  private val genStateHistogram: Gen[Any, MetricState[MetricKeyType.Histogram]] =
    Gen.const(MetricState.Histogram(Chunk((0, 100L), (Double.MaxValue, 200L)), 100L, 0.0, 100.0, 300.0d))

  // For a single pair we take a generated key and dependent on the key type we generate
  // a state matching the MetricKeyType
  private val genSinglePair: Gen[Sized, MetricPair.Untyped] =
    genKey
      .map(_.asInstanceOf[MetricKey.Untyped])
      .flatMap { key =>
        val lossy = key.asInstanceOf[MetricKey[MetricKeyType { type Out = Any }]]

        key.keyType match {
          case kc if kc.isInstanceOf[MetricKeyType.Counter]   => genStateCounter.map(MetricPair(lossy, _))
          case kg if kg.isInstanceOf[MetricKeyType.Gauge]     => genStateGauge.map(MetricPair(lossy, _))
          case kf if kf.isInstanceOf[MetricKeyType.Frequency] => genStateFrequency.map(MetricPair(lossy, _))
          case ks if ks.isInstanceOf[MetricKeyType.Summary]   => genStateSummary.map(MetricPair(lossy, _))
          case kh if kh.isInstanceOf[MetricKeyType.Histogram] => genStateHistogram.map(MetricPair(lossy, _))

          case _ => throw new RuntimeException("Boom")
        }
      }

  // Now we can generate a set of pairs for a more realistic state
  private val genMetricPairs: Gen[Sized, Set[MetricPair.Untyped]] =
    Gen.setOfBounded(1, 10)(genSinglePair)

  // A generator for Client Connect messages
  private val genConnect: Gen[Any, ClientMessage] = Gen.const(ClientMessage.Connect)

  // A generator for Connected messages
  private val genConnected: Gen[Sized, ClientMessage] = genNonEmpty.map(ClientMessage.Connected)

  // A generator for Disconnected messages
  private val genDisconnect: Gen[Sized, ClientMessage] = genNonEmpty.map(ClientMessage.Disconnect)

  // A generator for Subscription removals
  private val genRemoveSubscription: Gen[Sized, ClientMessage] =
    genNonEmpty.zip(genNonEmpty).map { case (con, sub) => ClientMessage.RemoveSubscription(con, sub) }

  // A generator for available keys
  private val genAvailableKeys: Gen[Sized, ClientMessage] =
    Gen.setOfBounded[Sized, MetricKey[Any]](1, 10)(genKey).map(ClientMessage.AvailableMetrics)

  // A generator for MetricsUpdates
  private val genNotification: Gen[Sized, ClientMessage] =
    genNonEmpty.zip(genNonEmpty).zip(genMetricPairs).map { case (cltId, subId, states) =>
      ClientMessage.MetricsNotification(cltId, subId, Instant.now().truncatedTo(ChronoUnit.MILLIS), states)
    }

  // A generator for random Client Messages
  private val genClientMsg: Gen[Sized, ClientMessage] =
    Gen.oneOf(genConnect, genConnected, genDisconnect, genRemoveSubscription, genAvailableKeys, genNotification)

  private val serdeMetricsLabel =
    test("the Metrics Labels should serialize to/from json correctly")(check(genLabel) { label =>
      label.toJson.fromJson[MetricLabel] match {
        case Right(l) => assertTrue(l.equals(label))
        case Left(_)  => assertTrue(false)
      }
    })

  private val serdeDuration =
    test("durations should serialize to/from json correctly")(check(genDuration) { d =>
      d.toJson.fromJson[Duration] match {
        case Right(d2) => assertTrue(d.equals(d2))
        case Left(_)   => assertTrue(false)
      }
    })

  private val serdeMetricKey =
    test("the MetricKeys should serialize to/from Json correctly")(check(genKey) { key =>
      key.toJson.fromJson[MetricKey[_]] match {
        case Right(k) =>
          assertTrue(k == key)
        case _        =>
          assertTrue(false)
      }
    })

  private val serdeClientMsg =
    test("the ClientMessages should serialize to/from Json correctly")(check(genClientMsg) { msg =>
      msg.toJson.fromJson[ClientMessage] match {
        case Right(m) =>
          assertTrue(m == msg)
        case Left(_)  =>
          assertTrue(false)
      }
    })
}
