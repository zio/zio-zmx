package zio.zmx.newrelic

import zio._
import zio.internal.metrics._
import zio.metrics.{MetricKey, MetricKeyType}
import zio.metrics.Metric
import zio.metrics.MetricListener
import zio.test._
import zio.zmx.Generators
import zio.zmx.Mocks._

import TestAspect._

object NewRelicListenerSpec extends ZIOSpecDefault with Generators {

  def spec = suite("NewRelicListenerSpec")(
    test("should properly push Counter metrics its underlying publisher.") {

      val counter   = Metric.counter("c1").fromConst(1L)
      val gauge     = Metric.gauge("g1")
      val histogram = Metric.histogram("h1", MetricKeyType.Histogram.Boundaries.linear(0, 1.0, 10))

      val pgm =
        for {
          listener       <- ZIO.service[NewRelicListener]
          _              <- ZIO.attempt(metricRegistry.installListener(listener))
          _              <- ZIO.unit @@ counter
          _              <- ZIO.unit @@ counter
          _              <- ZIO.succeed(1.0) @@ gauge
          _              <- ZIO.succeed(3.0) @@ gauge
          _              <- ZIO.succeed(1.0) @@ histogram
          _              <- ZIO.succeed(3.0) @@ histogram
          publsiher      <- ZIO.service[MockNewRelicPublisher]
          counterState   <- counter.value
          gaugeState     <- gauge.value
          histogramState <- histogram.value
          metrics         = publsiher.state

        } yield {
          println(metrics)
          assertTrue(
            metrics.size == 6,
            counterState.count == 2.0,
            gaugeState.value == 3.0,
            histogramState.count == 2L,
            histogramState.sum == 4.0,
            histogramState.min == 1.0,
            histogramState.max == 3.0,
          )
        }

      pgm
        .provide(MockNewRelicPublisher.mock, NewRelicListener.live)
    },
  ) @@ sequential

}
