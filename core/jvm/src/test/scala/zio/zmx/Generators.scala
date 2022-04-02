package zio.zmx

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
    count <- genPosDouble
  } yield {
    val state = MetricState.Counter(count)
    (MetricPair.unsafeMake(MetricKey.counter(name), state), state)
  }

  def genFrequency(count: Int, pastValues: Ref[Set[String]]) = for {
    name        <- nonEmptyString
    occurrences <- Gen.listOfN(count)(unqiueNonEmptyString(pastValues).flatMap(occName => genPosLong.map(occName -> _)))
  } yield {
    val asMap = occurrences.toMap

    // if (asMap.size < count)
    //   println(s"""|VILOTION!!!! occruences map was only ${asMap.size} in length, should have been count: $count
    //               |Original List: $occurrences
    //               |Map:           $asMap""".stripMargin)

    val state = MetricState.Frequency(asMap)
    (MetricPair.unsafeMake(MetricKey.frequency(name), state), state)
  }

  val genGauge = for {
    name  <- nonEmptyString
    count <- genPosDouble
  } yield {
    val state = MetricState.Gauge(count)
    (MetricPair.unsafeMake(MetricKey.counter(name), state), state)
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
