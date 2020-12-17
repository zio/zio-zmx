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
  ) @@ timed @@ timeout(10.seconds) @@ parallel

  private val emitCounters = testM("Emit Count Events")(for {
    ch     <- MetricsChannel.make(Clock.live)
    str     = ch.eventStream
    ff     <- str.run(ZSink.collectAll[TimedMetricEvent]).fork
    _      <- ZMX.count("test", 1.0d)
    _       = println("Waiting for stream to finish")
    flush  <- ch.flushMetrics(10.seconds).fork
    _      <- flush.join
    events <- ff.join
  } yield assert(events)(hasSize(equalTo(1))))
}
