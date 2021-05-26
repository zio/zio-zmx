package zio.zmx.metrics

import zio._
import zio.duration._

import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

import zio.zmx._
import zio.zmx.state.MetricType
import zio.zmx.state.DoubleHistogramBuckets

object HistogramSpec extends DefaultRunnableSpec with Generators {

  override def spec = suite("A ZMX Histogram should")(
    startFromZero,
    observe
  ) @@ timed @@ timeoutWarning(60.seconds) @@ parallel

  private val startFromZero = test("start from Zero") {
    val key = MetricKey.Histogram("fromZero", DoubleHistogramBuckets.linear(0d, 10d, 11).boundaries)
    metricState.getHistogram(key)

    checkHistogram(key, key.boundaries.map((_, 0L)), 0d, 0L)
  }

  private val observe = testM("observe correctly") {
    val key    = MetricKey.Histogram("increment", DoubleHistogramBuckets.linear(0d, 10d, 11).boundaries)
    val aspect = MetricAspect.observeInHistogram("increment", DoubleHistogramBuckets.linear(0d, 10d, 11).boundaries)
    for {
      _ <- ZIO.succeed(50d) @@ aspect
    } yield checkHistogram(key, key.boundaries.map(v => (v, if (v >= 50d) 1L else 0L)), 50d, 1L)
  }

  private def checkHistogram(
    key: MetricKey.Histogram,
    expectedCounts: Chunk[(Double, Long)],
    expectedSum: Double,
    expectedCount: Long
  ) = {
    val optHist   = snapshot().get(key)
    val isCorrect = optHist.get.details match {
      case h @ MetricType.DoubleHistogram(_, _, _) =>
        h.buckets.equals(expectedCounts) &&
          h.sum == expectedSum &&
          h.count == expectedCount
      case _                                       => false

    }
    assert(optHist)(isSome) && assert(isCorrect)(isTrue)
  }
}
