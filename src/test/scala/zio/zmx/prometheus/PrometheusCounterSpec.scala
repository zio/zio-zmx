package zio.zmx.prometheus

import zio.Chunk

import zio.duration._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.zmx.Generators

import PMetric._
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
    assert(checkCounter(cnt, 0d))(isTrue)
  }

  private val incByOne = test("increment by 1 as default") {
    val cnt = incCounter(counter("incOne", "", Chunk.empty)).get
    assert(checkCounter(cnt, 1d))(isTrue)
  }

  private val incPositive = testM("Increment by an arbitrary double value")(check(genPosDouble) { v =>
    val cnt = incCounter(counter("incDouble", "", Chunk.empty), v).get
    assert(checkCounter(cnt, v))(isTrue)
  })

  private val notIncNegative = testM("Do not accept a negative increment")(check(genNegDouble) { v =>
    val cnt = incCounter(counter("incNeg", "", Chunk.empty), v)
    assert(cnt)(isNone)
  })

  private val incSome = testM("Properly add subsequent increments")(check(genSomeDoubles(10)) { sd =>
    val cnt = sd.foldLeft(counter("countSome", "", Chunk.empty)) { case (cur, d) => incCounter(cur, d).get }
    assert(checkCounter(cnt, sd.fold(0d)(_ + _)))(isTrue)
  })

  private def checkCounter(m: PMetric, v: Double): Boolean = m.details match {
    case c: PMetric.Counter => c.count == v
    case _                  => false
  }

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
