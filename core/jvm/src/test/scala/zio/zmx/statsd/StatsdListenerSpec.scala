package zio.zmx.statsd

import zio._
import zio.metrics._
import zio.test._
import zio.test.TestAspect._

class TestStatsdClient(events: Ref[Chunk[String]], filter: String => Boolean) extends StatsdClient {

  override private[statsd] def write(s: String): Long = {
    if (filter(s)) {
      println(s)
      Runtime.default.unsafeRun(events.update(_ :+ s))
    }
    s.size.toLong
  }
}

object StatsdListenerSpec extends ZIOSpecDefault {

  override def spec = suite("The StatsdListener should")(
    sendCounter,
    sendGauge,
  ) @@ timed @@ timeoutWarning(60.seconds)

  private def testMetric(k: MetricKey[_], m: Metric[_, Any, _]) =
    for {
      events  <- Ref.make[Chunk[String]](Chunk.empty)
      listener = StatsdListener.make(new TestStatsdClient(events, _.contains(k.name)))
      _        = MetricClient.unsafeInstallListener(listener)
      _       <- ZIO.unit @@ m
      _        = MetricClient.unsafeRemoveListener(listener)
      res     <- events.get
    } yield res

  private val sendCounter = test("send counter updates") {
    val name = "testCounter"
    testMetric(MetricKey.counter(name), Metric.counter(name).contramap[Any](_ => 1L)).map(res =>
      assertTrue(res.size == 1) &&
        assertTrue(res.head.equals(s"$name:1|c")),
    )
  }

  private val sendGauge = test("send gauge updates") {
    val name = "testGauge"
    testMetric(MetricKey.gauge(name), Metric.gauge(name).contramap[Any](_ => 1L)).map(res =>
      assertTrue(res.size == 1) &&
        assertTrue(res.head.equals(s"$name:1|g")),
    )
  }
}
