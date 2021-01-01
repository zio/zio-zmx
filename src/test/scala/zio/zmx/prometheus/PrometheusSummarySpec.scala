package zio.zmx.prometheus

import zio.Chunk

import zio.duration._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.zmx.Generators

import PMetric._
import zio.clock._

object PrometheusSummarySpec extends DefaultRunnableSpec with Generators {

  override def spec = suite("The Prometheus Summary should")(
    allowNoQuantiles,
    prohibitQuantileLabel,
    observeOne,
    encode
  ) @@ timed @@ timeoutWarning(60.seconds) @@ parallel

  private val allowNoQuantiles = test("allow no quantiles") {
    val s = (summary("noQuantiles", "Summary help", Chunk.empty)()).get
    assert(s.details.isInstanceOf[PMetric.Summary])(isTrue) &&
    assert(s.details.asInstanceOf[PMetric.Summary].quantiles)(isEmpty)
  }

  private val prohibitQuantileLabel = test("prohibit quantile label") {
    val s = summary("noQuantileLabel", "Summary Help", Chunk("quantile" -> "foo"))()
    assert(s)(isNone)
  }

  private val observeOne = testM("observe one value")(checkM(Gen.anyDouble) { v =>
    for {
      now <- instant
      s    = summary("observeOne", "Histogram Help", Chunk.empty)().get
      s2   = observeSummary(s, v, now).get
    } yield assert(asSummary(s2.details).count)(equalTo(1d)) &&
      assert(asSummary(s2.details).sum)(equalTo(v))
  })

  private def asSummary(m: PMetric.Details) = m.asInstanceOf[PMetric.Summary]

  private val encode = testM("Properly encode the summary for Prometheus")(checkM(Gen.anyDouble) { v =>
    for {
      now    <- instant
      name    = "encSummary"
      s       = summary(name, "Summary Help", Chunk.empty)().get
      s2      = observeSummary(s, v, now).get
      encoded = PrometheusEncoder.encode(List(s2), now)
      lines   = encoded.split("\n")
    } yield assert(
      encoded.startsWith(
        s"""# TYPE $name summary
           |# HELP $name Summary Help""".stripMargin
      )
    )(isTrue) &&
      assert(encoded.endsWith(s"""${name}_sum $v 0
                                 |${name}_count 1.0 0""".stripMargin))(isTrue)
  })
}
