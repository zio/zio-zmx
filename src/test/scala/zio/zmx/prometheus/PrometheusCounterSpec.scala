package zio.zmx.prometheus

import zio.Chunk

import zio.duration._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

import Metric._
import zio.clock._

object PrometheusCounterSpec extends DefaultRunnableSpec with Generators {

  override def spec = suite("The Prometheus Counter should")(
    startFromZero,
    incByOne,
    incPositive,
    notIncNegative,
    incSome,
    encode
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

  private val incSome = testM("Properly add subsequent increments")(check(genSomeDoubles(10)) { sd =>
    val cnt = sd.foldLeft(counter("countSome", "", Chunk.empty)) { case (cur, d) => incCounter(cur, d).get }
    assert(cnt.details.count)(equalTo(sd.fold(0d)(_ + _)))
  })

  private val encode = testM("Properly encode the counter for Prometheus")(checkM(genPosDouble) { v =>
    for {
      now    <- instant
      cnt     = incCounter(counter("incDouble", "Help me", Chunk.empty), v).get
      encoded = PrometheusEncoder.encode(List(cnt), now)
    } yield assert(encoded)(
      equalTo(
        s"""# TYPE incDouble counter
           |# HELP incDouble Help me
           |incDouble ${v} 0""".stripMargin
      )
    )
  })
}
