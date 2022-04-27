package zio.zmx.prometheus

import zio._
import zio.metrics._
import zio.test._
import zio.test.TestAspect._
import zio.zmx._
import zio.zmx.prometheus.PrometheusEncoder

object PrometheusEncoderSpec extends ZIOSpecDefault with Generators {

  private val encoder = PrometheusEncoder.make

  override def spec = suite("The Prometheus encoding should")(
    encodeCounter,
    // encodeHistogram
  ) @@ timed @@ timeoutWarning(60.seconds) @@ parallel

  private val encodeCounter = test("Encode a Counter")(check(genPosDouble) { v =>
    for {
      event <- ZIO
                 .clockWith(_.instant)
                 .map(now => MetricEvent.New(MetricKey.counter("countMe"), MetricState.Counter(v), now))
      text  <- encoder.encode(event)
    } yield assertTrue(
      text.equals(
        Chunk(
          "# TYPE countMe counter",
          "# HELP countMe Some help",
          s"countMe $v ${event.timestamp.toEpochMilli()}",
        ),
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
