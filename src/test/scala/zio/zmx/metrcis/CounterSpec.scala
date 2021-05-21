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
    checkCounter(key, 0.0d)
  }

  private val increment = testM("increment correctly")(for {
    key <- ZIO.succeed(MetricKey.Counter("increment"))
    asp  = MetricAspect.count(key)
    _   <- ZIO.succeed(None) @@ asp
  } yield checkCounter(key, 1d))

  private def checkCounter(key: MetricKey.Counter, expected: Double) = {
    val optCounter = snapshot().get(key)
    assert(optCounter)(isSome) && assert(optCounter.get.details)(equalTo(MetricType.Counter(expected)))
  }
}
