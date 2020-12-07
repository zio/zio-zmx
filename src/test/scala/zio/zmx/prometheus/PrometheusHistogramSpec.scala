package zio.zmx.prometheus

import zio.Chunk

import zio.duration._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

import PMetric._
import zio.clock._

object PrometheusHistogramSpec extends DefaultRunnableSpec with Generators {

  override def spec = suite("The Prometheus Histogram should")(
    allowNoBuckets,
    prohibitLeLabel,
    observeOne,
    encode
  ) @@ timed @@ timeoutWarning(60.seconds) @@ parallel

  private val allowNoBuckets = test("allow no buckets") {
    val h = histogram("noBuckets", "Histogram Help", Chunk.empty, BucketType.Manual()).get
    assert(h.details.buckets.map(_._1))(equalTo(Chunk(Double.MaxValue)))
  }

  private val prohibitLeLabel = test("prohibit le label") {
    val h = histogram("no buckets", "Histogram Help", Chunk("le" -> "foo"), BucketType.Manual())
    assert(h)(isNone)
  }

  private val observeOne = testM("observe one value")(checkM(Gen.anyDouble) { v =>
    for {
      now <- instant
      h    = histogram("observeOne", "Histogram Help", Chunk.empty, BucketType.Linear(0, 10, 10)).get
      h2   = observeHistogram(h, v, now)
    } yield assert(h2.details.count)(equalTo(1d)) &&
      assert(h2.details.sum)(equalTo(v)) &&
      assert(h2.details.buckets.length)(equalTo(11)) &&
      // The value must only be recorded for all relevant buckets
      assert(h2.details.buckets.forall { case (l, ts) =>
        (v <= l && ts.samples.length == 1) || (v > l && ts.samples.isEmpty)
      })(isTrue)
  })

  private val encode = testM("Properly encode the histogram for Prometheus")(checkM(Gen.anyDouble) { v =>
    for {
      now    <- instant
      h       = histogram("observeOne", "Histogram Help", Chunk.empty, BucketType.Linear(0, 10, 10)).get
      h2      = observeHistogram(h, v, now)
      encoded = PrometheusEncoder.encode(List(h2), now)
      lines   = encoded.split("\n")
    } yield assert(
      encoded.startsWith(
        s"""# TYPE observeOne histogram
           |# HELP observeOne Histogram Help""".stripMargin
      )
    )(isTrue) &&
      assert(encoded.endsWith(s"""observeOne_sum $v 0
                                 |observeOne_count 1.0 0""".stripMargin))(isTrue) &&
      assert(
        h2.details.buckets
          .map(_._1)
          .map(d => if (d < Double.MaxValue) d.toString else "+Inf")
          .forall(d => lines.exists(s => s.startsWith("observeOne{le=\"" + d + "\"}")))
      )(isTrue)
  })
}
