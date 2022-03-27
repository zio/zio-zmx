package zio.test.scala

import zio._
import zio.json._
import zio.metrics._

import zio.test._
import zio.test.TestAspect._

import zio.zmx.client.MetricsMessageImplicits._

object MetricsMessageSpec extends DefaultRunnableSpec {

  def spec = suite("For MetricsMessages")(
    serdeMetricsLabel,
    serdeDuration,
    serdeMetricKey
  ) @@ timed @@ parallel

  // a generator for Durations
  private val genDuration = Gen.long(100L, 1000L).map(l => Duration.fromMillis(l))

  private val genNonEmpty = Gen.alphaNumericStringBounded(1, 10)

  // Just generator Shortcut for MetricsLabels
  private val genLabel = genNonEmpty.zip(genNonEmpty).map { case (k, v) => MetricLabel(k, v) }

  // A generator for counter keys
  private val genKeyCounter   =
    genNonEmpty.zip(genLabel).map { case (name, label) => MetricKey.counter(name).tagged(label) }

  // A generator for gauge keys
  private val genKeyGauge     =
    genNonEmpty.zip(genLabel).map { case (name, label) => MetricKey.gauge(name).tagged(label) }

  // A generator for frequency keys
  private val genKeyFrequency =
    genNonEmpty.zip(genLabel).map { case (name, label) => MetricKey.frequency(name).tagged(label) }

  // A generator for histogram keys
  private val genKeyHistogram =
    genNonEmpty.zip(genLabel).map { case (name, label) =>
      MetricKey.histogram(name, MetricKeyType.Histogram.Boundaries.linear(0, 10, 11)).tagged(label)
    }

  // A generator for histogram keys
  private val genKeySummary =
    genNonEmpty.zip(genLabel).map { case (name, label) =>
      MetricKey.summary(name, Duration.fromMillis(1000L), 100, 0.03d, Chunk(0.1, 0.5, 0.95)).tagged(label)
    }

  // A generator for MetricKeys
  private val genKey: Gen[Random with Sized, MetricKey[Any]] =
    Gen.oneOf(genKeyCounter, genKeyGauge, genKeyFrequency, genKeyHistogram, genKeySummary)

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
          assertTrue(k.equals(key))
        case _        =>
          assertTrue(false)
      }
    })
}
