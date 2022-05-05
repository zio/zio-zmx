package zio.zmx.statsd

import zio._
import zio.metrics._
import zio.test._
import zio.test.TestAspect._
import zio.zmx._

object StatsdEncoderSpec extends ZIOSpecDefault {

  private val makeListener =
    Ref
      .make[Chunk[Byte]](Chunk.empty)
      .map(buf =>
        new MetricListener[Byte] {
          override val encoder   = StatsdEncoder
          override val publisher = CollectingPublisher[Byte](buf)
        },
      )

  override def spec = suite("The StatsdEncoder should")(
    sendCounter,
    sendGauge,
  ) @@ timed @@ timeoutWarning(60.seconds)

  private def testMetric(k: MetricKey.Untyped, m: MetricState.Untyped) =
    for {
      listener <- makeListener
      event    <- ZIO.clockWith(_.instant).map(now => MetricEvent.New(k, m, now))
      _        <- listener.update(Set(event))
      encoded  <- listener.publisher.asInstanceOf[CollectingPublisher[Byte]].get
    } yield new String(encoded.toArray)

  private val sendCounter = test("send counter updates") {
    val name = "testCounter"
    testMetric(MetricKey.counter(name), MetricState.Counter(1)).map(res => assertTrue(res.equals(s"$name:1|c")))
  }

  private val sendGauge = test("send gauge updates") {
    val name = "testGauge"
    testMetric(MetricKey.gauge(name), MetricState.Gauge(1)).map(res => assertTrue(res.equals(s"$name:1|g")))
  }
}
