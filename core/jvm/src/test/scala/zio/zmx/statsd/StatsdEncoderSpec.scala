package zio.zmx.statsd

import java.time.Instant

import zio._
import zio.metrics._
import zio.test._
import zio.test.TestAspect._

object StatsdEncoderSpec extends ZIOSpecDefault {

  override def spec = suite("The StatsdEncoder should")(
    sendCounter,
    sendGauge,
  ) @@ timed @@ timeoutWarning(60.seconds)

  private def testMetric(k: MetricKey[_], m: Metric[_, Any, _]) =
    for {
      events <- Ref.make[Chunk[String]](Chunk.empty)
      _      <- ZIO.unit @@ m
      events  = MetricClient.unsafeSnapshot().filter(_.metricKey.equals(k))
      res     = StatsdEncoder.encode(events, Instant.now())
    } yield res

  private val sendCounter = test("send counter updates") {
    val name = "testCounter"
    testMetric(MetricKey.counter(name), Metric.counter(name).contramap[Any](_ => 1L)).map(res =>
      assertTrue(res.equals(s"$name:1|c")),
    )
  }

  private val sendGauge = test("send gauge updates") {
    val name = "testGauge"
    testMetric(MetricKey.gauge(name), Metric.gauge(name).contramap[Any](_ => 1L)).map(res =>
      assertTrue(res.equals(s"$name:1|g")),
    )
  }
}
