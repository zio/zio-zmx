package zio.zmx.metrics

import zio._
import zio.duration._

import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

import zio.zmx._
import zio.zmx.metrics.MetricKey
import zio.zmx.state.MetricType

object CounterSpec extends DefaultRunnableSpec with Generators {

  override def spec = suite("A ZMX Counter should")(
    startFromZero,
    increment
  ) @@ timed @@ timeoutWarning(60.seconds) @@ parallel

  private val startFromZero = test("start from Zero") {
    val key = MetricKey.Counter("fromZero")
    metricState.getCounter(key)

    assert(checkCounter(key, 0.0d))(isTrue)
  }

  private val increment = testM("increment correctly")(for {
    key <- ZIO.succeed(MetricKey.Counter("increment"))
    asp  = MetricAspect.count(key)
    _   <- ZIO.succeed(None) @@ asp
  } yield assert(checkCounter(key, 1.0d))(isTrue))

  private def checkCounter(key: MetricKey.Counter, expected: Double): Boolean = snapshot().get(key) match {
    case Some(c) =>
      c.details match {
        case MetricType.Counter(v) => c.name == key.name && v == expected
        case _                     => false
      }
    case None    => false
  }
}
