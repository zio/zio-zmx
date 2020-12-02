package zio.zmx.prometheus

import zio.clock._
import zio.duration._

import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

object TimeSeriesSpec extends DefaultRunnableSpec with Generators {

  override def spec = suite("A Timeseries should")(
    startEmpty,
    recordSome,
    obeyMaxSize,
    obeyMaxAge
  ) @@ timed @@ timeoutWarning(60.seconds) @@ parallel

  // Make sure that a time series starts empty
  private val startEmpty = zio.test.test("start Empty") {
    val ts = TimeSeries()
    assert(ts.samples.isEmpty)(isTrue)
  }

  // Record a number of values up to the maximum size of the TimeSeries
  private val recordSome =
    testM("Record Some")(checkM(genSomeDoubles(TimeSeries.defaultMaxSize - 1)) { seq =>
      for {
        now <- instant
        ts   = seq.foldLeft(TimeSeries()) { case (c, d) => c.observe(d, now) }
      } yield assert(ts.samples.length)(equalTo(seq.length))
    })

  private val obeyMaxSize = testM("Obey the chosen maximum size")(checkM(Gen.int(1, 100)) { s =>
    (for {
      now <- instant
      ts   = 0.to(s + 1).foldLeft(TimeSeries(maxSize = s)) { case (c, i) => c.observe(i.toDouble, now) }
    } yield assert(ts.samples.length)(equalTo(s)))
  })

  private val obeyMaxAge = testM("Obey the chosen maxAge")(checkM(Gen.int(61, TimeSeries.defaultMaxSize - 1)) { n =>
    for {
      ts <- zio.ZIO.succeed(0L.until(n.longValue).foldLeft(TimeSeries(maxAge = 60.seconds)) { case (c, i) =>
              c.observe(i.toDouble, java.time.Instant.ofEpochSecond(i))
            })
    } yield assert(ts.timedSamples(java.time.Instant.ofEpochSecond(n.longValue), Some(60.seconds)).length)(
      equalTo(60)
    )
  })

}
