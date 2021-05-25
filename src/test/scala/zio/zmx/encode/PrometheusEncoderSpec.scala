package zio.zmx.encode

import java.time.Instant

import zio.Chunk
import zio.test.DefaultRunnableSpec
import zio.zmx.Generators

import zio.duration._
import zio.test._
import zio.test.TestAspect._
import zio.test.Assertion._

import zio.zmx.state.MetricState
import zio.zmx.metrics.MetricKey

object PrometheusEncoderSpec extends DefaultRunnableSpec with Generators {

  override def spec = suite("The Prometheus encoding should")(
    encodeCounter
  ) @@ timed @@ timeoutWarning(60.seconds) @@ parallel

  private val encodeCounter = testM("Encode a Counter")(check(genPosDouble) { v =>
    val state = Chunk(MetricState.counter(MetricKey.Counter("countMe"), "Help me", v))
    val i     = Instant.now()
    val text  = PrometheusEncoder.encode(state, i)

    assert(text)(
      equalTo(
        s"""# TYPE countMe counter
           |# HELP countMe Help me
           |countMe $v ${i.toEpochMilli()}""".stripMargin
      )
    )
  })
}
