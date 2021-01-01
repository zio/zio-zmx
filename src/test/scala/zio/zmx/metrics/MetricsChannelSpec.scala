package zio.zmx.metrics

import zio._
import zio.clock._
import zio.zmx.Generators
import zio.duration._
import zio.stream._
import zio.zmx.metrics.MetricsDataModel._

import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.environment.{ Live, TestClock }

object MetricsChannelSpec extends DefaultRunnableSpec with Generators {

  override def spec = suite("The ZMX Metrics Channel should")(
    emitCounters,
    emitSetGauge,
    emitChangeGauge
  ) @@ timed @@ timeout(10.seconds) @@ parallel

  private val emitCounters = checkEmit(
    "Counter",
    MetricEvent("myCounter", MetricEventDetails.count(1.0d).get)
  )

  private val emitSetGauge = checkEmit(
    "SetGauge",
    MetricEvent("myGauge", MetricEventDetails.gaugeChange(1.0d, false))
  )

  private val emitChangeGauge = checkEmit(
    "SetGauge",
    MetricEvent("myGauge", MetricEventDetails.gaugeChange(1.0d, true))
  )

  private def checkEmit(hint: String, expected: MetricEvent) =
    testM(s"Emit $hint Events")(for {
      _      <- timeWarp.fork
      ch     <- MetricsChannel.make(Clock.live)
      str     = ch.eventStream
      ff     <- str.run(ZSink.collectAll[TimedMetricEvent]).fork
      _      <- ch.record(expected)
      flush  <- ch.flushMetrics(10.seconds).fork
      _      <- flush.join
      events <- ff.join
    } yield assert(events)(hasSize(equalTo(2))) && assert(events.head.event)(equalTo(expected)))

  private def timeWarp = for {
    _ <-
      Live
        .withLive(TestClock.adjust(java.time.Duration.ofSeconds(1)))(_.repeat(Schedule.spaced(100.millis)))
  } yield ()
}
