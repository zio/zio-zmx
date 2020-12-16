package zio.zmx.metrics

import zio.zmx.Generators
import zio.clock._
import zio.duration._
import zio.stream._

import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.zmx.metrics.MetricsDataModel._

object MetricsChannelSpec extends DefaultRunnableSpec with Generators {

  override def spec = suite("The ZMX Metrics Channel should")(
    emitCounters
  ) @@ timed @@ timeoutWarning(60.seconds) @@ parallel

  private val emitCounters = testM("Emit Count Events")(for {
    ch     <- MetricsChannel.make(Clock.live)
    ff     <- ch.eventStream.frun(ZSink.collectAll[TimedMetricEvent]).fork
    _      <- ZMX.count("test", 1.0d)
    _      <- ch.flushMetrics(10.seconds)
    events <- ff.join
  } yield assert(events)(hasSize(equalTo(1))))
}
