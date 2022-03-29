package zio.zmx.prometheus

import java.time.Instant

import zio._
import zio.metrics._
import zio.test._
import zio.test.DefaultRunnableSpec
import zio.test.TestAspect._
import zio.zmx.Generators
import zio.zmx.prometheus.PrometheusEncoder

object PrometheusEncoderSpec extends DefaultRunnableSpec with Generators {

  override def spec = suite("The Prometheus encoding should")(
    encodeCounter,
    // encodeHistogram
  ) @@ timed @@ timeoutWarning(60.seconds) @@ parallel

  private val encodeCounter = test("Encode a Counter")(check(genPosDouble) { v =>
    val state = Chunk(MetricPair.unsafeMake(MetricKey.counter("countMe"), MetricState.Counter(v)))
    val i     = Instant.now()
    val text  = PrometheusEncoder.encode(state, i)

    assertTrue(
      text.equals(
        s"""# TYPE countMe counter
           |# HELP countMe Some help
           |countMe $v ${i.toEpochMilli()}""".stripMargin,
      ),
    )
  })

  // private val encodeHistogram = testM("Encode a Histogram")(check(genPosDouble) { v =>
  //   val histogram = MetricKey
  //     .Histogram("test", ZIOMetric.Histogram.Boundaries.linear(0.1, 1.0, 10), Chunk(MetricLabel("label", "x")))

  //   val encoded = PrometheusEncoder.encode(MetricState.histogram(histogram), Instant.ofEpochMilli(0))
  //   val lines   = encoded.split("\n")

  //   assertTrue(
  //     encoded.startsWith(
  //       s"""# TYPE test histogram
  //          |# HELP test Test histogram""".stripMargin
  //     )
  //   ) &&
  //   assertTrue(encoded.endsWith(s"""test_sum{label="x"} $v 0
  //                                  |test_count{label="x"} 1.0 0""".stripMargin)) &&
  //   assertTrue(
  //     (bounds :+ Double.MaxValue)
  //       .map(d => if (d < Double.MaxValue) d.toString else "+Inf")
  //       .forall(d => lines.exists(s => s.startsWith(s"""test_bucket{label="x",le="$d"}""")))
  //   )
  // })
}
