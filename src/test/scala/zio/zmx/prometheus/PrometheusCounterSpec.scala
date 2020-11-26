package zio.zmx.prometheus

import zio.Chunk

import zio.duration._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

import Metric._

// Requirements see https://prometheus.io/docs/instrumenting/writing_clientlibs/#counter
object PrometheusCounterSpec extends DefaultRunnableSpec {

  private val genPosDouble = Gen.double(0.0, Double.MaxValue)
  private val genNegDouble = Gen.double(Double.MinValue, 0.0)

  override def spec = suite("The Prometheus Counter should")(
    startFromZero,
    incByOne,
    incPositive,
    notIncNegative
  ) @@ timed @@ timeoutWarning(60.seconds) @@ parallel

  private val startFromZero = test("start from Zero") {
    val cnt = counter("fromZero", "", Chunk.empty)
    assert(cnt.details.count)(equalTo(0.0d))
  }

  private val incByOne = test("increment by 1 as default") {
    val cnt = incCounter(counter("incOne", "", Chunk.empty)).get
    assert(cnt.details.count)(equalTo(1.0d))
  }

  private val incPositive = testM("Increment by an arbitrary double value")(check(genPosDouble) { v =>
    val cnt = incCounter(counter("incDouble", "", Chunk.empty), v).get
    assert(cnt.details.count)(equalTo(v))
  })

  private val notIncNegative = testM("Do not accept a negative increment")(check(genNegDouble) { v =>
    val cnt = incCounter(counter("incNeg", "", Chunk.empty), v)
    assert(cnt)(isNone)
  })
}
