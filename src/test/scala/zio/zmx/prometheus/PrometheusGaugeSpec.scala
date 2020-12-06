package zio.zmx.prometheus

import zio.Chunk

import zio.duration._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

import zio.clock._

import Metric._

object PrometheusGaugeSpec extends DefaultRunnableSpec with Generators {

  override def spec = suite("The Prometheus Gauge should")(
    startFromZero,
    incByOne,
    decByOne,
    incPositive,
    incSome,
    encode
  ) @@ timed @@ timeoutWarning(60.seconds) @@ parallel

  private val startFromZero = test("start from Zero") {
    val g = gauge("fromZero", "", Chunk.empty)
    assert(g.details.value)(equalTo(0.0d))
  }

  private val incByOne = test("increment by 1 as default") {
    val g = incGauge(gauge("incOne", "", Chunk.empty))
    assert(g.details.value)(equalTo(1.0d))
  }

  private val decByOne = test("decrement by 1 as default") {
    val g = decGauge(gauge("decOne", "", Chunk.empty))
    assert(g.details.value)(equalTo(-1.0d))
  }

  private val incPositive = testM("Increment by an arbitrary double value")(check(Gen.anyDouble) { v =>
    val g = incGauge(gauge("incDouble", "", Chunk.empty), v)
    assert(g.details.value)(equalTo(v))
  })

  private val incSome = testM("Properly add subsequent increments")(check(Gen.chunkOfN(10)(Gen.anyDouble)) { sd =>
    val g = sd.foldLeft(gauge("countSome", "", Chunk.empty)) { case (cur, d) => incGauge(cur, d) }
    assert(g.details.value)(equalTo(sd.fold(0d)(_ + _)))
  })

  private val encode = testM("Properly encode the gauge for Prometheus")(checkM(Gen.anyDouble) { v =>
    for {
      now    <- instant
      cnt     = incGauge(gauge("myGauge", "Help me", Chunk.empty), v)
      encoded = PrometheusEncoder.encode(List(cnt), now)
    } yield assert(encoded)(
      equalTo(
        s"""# TYPE myGauge gauge
           |# HELP myGauge Help me
           |myGauge ${v} 0""".stripMargin
      )
    )
  })

}
