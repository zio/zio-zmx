package zio.zmx.encode

import java.time.Instant
import zio.Chunk
import zio.test.DefaultRunnableSpec
import zio.zmx.Generators
import zio.zmx.prometheus.PrometheusEncoder
import zio.duration._
import zio.test._
import zio.test.TestAspect._
import zio.test.Assertion._
import zio.zmx.internal.{ ConcurrentHistogram, MetricKey }
import zio.zmx.state.MetricState

object PrometheusEncoderSpec extends DefaultRunnableSpec with Generators {

  override def spec = suite("The Prometheus encoding should")(
    encodeCounter,
    encodeHistogram
  ) @@ timed @@ timeoutWarning(60.seconds) @@ parallel

  private val encodeCounter = testM("Encode a Counter")(check(genPosDouble) { v =>
    val state = Chunk(MetricState.counter(MetricKey.Counter("countMe"), "Help me", v))
    val i     = Instant.now()
    val text  = PrometheusEncoder.encode(state, i)

    assert(text.value)(
      equalTo(
        s"""# TYPE countMe counter
           |# HELP countMe Help me
           |countMe $v ${i.toEpochMilli()}""".stripMargin
      )
    )
  })

  private val encodeHistogram = testM("Encode a Histogram")(check(genPosDouble) { v =>
    val bounds    = Chunk(0.1, 1.0, 10.0)
    val histogram = ConcurrentHistogram.manual(bounds)
    histogram.observe(v)
    val state     = MetricState.doubleHistogram(
      MetricKey.Histogram("test", bounds, Chunk("label" -> "x")),
      "Test histogram",
      histogram.snapshot(),
      histogram.count(),
      histogram.sum()
    )
    val encoded   = PrometheusEncoder.encode(Chunk(state), Instant.ofEpochMilli(0))
    val lines     = encoded.value.split("\n")

    assert(
      encoded.value.startsWith(
        s"""# TYPE test histogram
           |# HELP test Test histogram""".stripMargin
      )
    )(isTrue) &&
    assert(encoded.value.endsWith(s"""test_sum{label="x"} $v 0
                                     |test_count{label="x"} 1.0 0""".stripMargin))(isTrue) &&
    assert(
      (bounds :+ Double.MaxValue)
        .map(d => if (d < Double.MaxValue) d.toString else "+Inf")
        .forall(d => lines.exists(s => s.startsWith(s"""test_bucket{label="x",le="$d"}""")))
    )(isTrue)
  })
}
