package zio.zmx.metrics

import zio._
import zio.duration._

import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

import zio.zmx._
import zio.zmx.metrics.MetricKey
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

  private val observe = testM("observe correctly")(for {
    key <- ZIO.succeed(MetricKey.Histogram("increment", DoubleHistogramBuckets.linear(0d, 10d, 11).boundaries))
    asp  = MetricAspect.observeInHistogram[Double](key)(ZIO.succeed(_))
    _   <- ZIO.succeed(50d) @@ asp
  } yield checkHistogram(key, key.boundaries.map(v => (v, if (v >= 50d) 1L else 0L)), 50d, 1L))

  private def checkHistogram(
    key: MetricKey.Histogram,
    expectedCounts: Chunk[(Double, Long)],
    expectedSum: Double,
    expectedCount: Long
  ) =
    snapshot().get(key) match {
      case Some(c) =>
        c.details match {
          case h @ MetricType.DoubleHistogram(_, _, _) =>
            assert(c.name)(equalTo(key.name)) &&
              assert(h.buckets)(equalTo(expectedCounts)) &&
              assert(h.sum)(equalTo(expectedSum)) &&
              assert(h.count)(equalTo(expectedCount))
          case _                                       => assert(true)(isFalse)
        }
      case None    => assert(true)(isFalse)
    }
}
