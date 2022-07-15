package zio.metrics.connectors

import zio._
import zio.metrics._
import zio.test._

trait Generators {
  val genPosDouble           = Gen.double(0.0, Double.MaxValue)
  val genNegDouble           = Gen.double(Double.MinValue, 0.0)
  def genSomeDoubles(n: Int) = Gen.chunkOfBounded(1, n)(genPosDouble)

  val genPosLong = Gen.long(0L, Long.MaxValue)

  val nonEmptyString = Gen.alphaNumericString.filter(_.nonEmpty)

  val genCounter = for {
    name  <- nonEmptyString
    count <- Gen.double(1, 100)
  } yield {
    val state = MetricState.Counter(count)
    (Unsafe.unsafeCompat(implicit u => MetricPair.make(MetricKey.counter(name), state)), state)
  }

  def genCounterNamed(name: String, min: Double = 1.0, max: Double = 100) = for {
    count <- Gen.double(min, max)
  } yield {
    val state = MetricState.Counter(count)
    (Unsafe.unsafeCompat(implicit u => MetricPair.make(MetricKey.counter(name), state)), state)
  }

  def genFrequency(count: Int, pastValues: Ref[Set[String]]) = for {
    name        <- nonEmptyString
    occurrences <- Gen.listOfN(count)(unqiueNonEmptyString(pastValues).flatMap(occName => genPosLong.map(occName -> _)))
  } yield {
    val asMap = occurrences.toMap
    val state = MetricState.Frequency(asMap)
    (Unsafe.unsafeCompat(implicit u => MetricPair.make(MetricKey.frequency(name), state)), state)
  }

  val genFrequency1 = for {
    name    <- nonEmptyString
    occName <- nonEmptyString
    count   <- genPosLong
  } yield {

    val asMap = Map(occName -> count)
    val state = MetricState.Frequency(asMap)
    (Unsafe.unsafeCompat(implicit u => MetricPair.make(MetricKey.frequency(name), state)), state)
  }

  val genGauge = for {
    name  <- nonEmptyString
    count <- genPosDouble
  } yield {
    val state = MetricState.Gauge(count)
    (Unsafe.unsafeCompat(implicit u => MetricPair.make(MetricKey.counter(name), state)), state)
  }

  def genHistogram = for {
    name  <- nonEmptyString
    count <- genPosLong
    min   <- Gen.double
    max   <- Gen.double.filter(_ >= min)
    sum    = (min + max)
  } yield {
    val boundaries = MetricKeyType.Histogram.Boundaries.linear(0, 10, 11)
    val buckets    = Chunk(
      (0.0, 0L),
      (1.0, 1L),
      (2.0, 2L),
      (3.0, 3L),
      (4.0, 4L),
      (5.0, 5L),
      (6.0, 6L),
      (7.0, 7L),
      (8.0, 8L),
      (9.0, 9L),
      (10.0, 10L),
      (Double.MaxValue, 10L),
    )
    val state      = MetricState.Histogram(buckets, count, min, max, sum)
    (Unsafe.unsafeCompat(implicit u => MetricPair.make(MetricKey.histogram(name, boundaries), state)), state)
  }

  def unqiueNonEmptyString(ref: Ref[Set[String]]) =
    Gen(
      nonEmptyString.sample.filterZIO {
        case Some(s) =>
          for {
            exists <- ref.get.map(_.contains(s.value))
            _      <- if (!exists) ref.update(_ + s.value) else ZIO.unit
          } yield !exists
        case _       => ZIO.succeed(true)
      },
    )

}
