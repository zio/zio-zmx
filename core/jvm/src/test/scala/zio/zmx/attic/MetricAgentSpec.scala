package zio.zmx

import java.util.concurrent.TimeUnit

import zio._
import zio.metrics._
import zio.stream._
import zio.test._

import TestAspect._
import testing._

object MetricAgentSpec extends ZIOSpecDefault with Generators {
  import Mocks._

  def spec = suite("MetricAgentSpec")(
    behaviorSuite,
    perfSuite,
  )

  val behaviorSuite = suite("Behavior")(
    test("Should publish MetricPairs.") {
      check(genCounter, genGauge) { case ((pair1, _), (pair2, _)) =>
        val testCase =
          for {
            agent          <- ZIO.service[MetricAgent[String]]
            publisher      <- ZIO.service[MockMetricPublisher[String]]
            encoder        <- ZIO.service[MockMetricEncoder[String]]
            registry       <- ZIO.service[MockMetricRegistry]
            ts1            <- Clock.currentTime(TimeUnit.MILLISECONDS)
            ts2             = ts1 + 1000
            fiber          <- agent.runAgent
            _              <- registry.putMetric(pair1 -> ts1, pair2 -> ts2)
            _              <- TestClock.adjust(1.minute)
            _              <- fiber.interrupt
            encoderInput   <- encoder.state
            publisherInput <- publisher.state

          } yield {

            val expectedEncoderInput   = Chunk((pair1, ts1), (pair2, ts2))
            val expectedPublisherInput = Chunk(Chunk(s"${pair1.metricKey}-$ts1", s"${pair2.metricKey}-$ts2"))

            assertTrue(
              true,
              encoderInput.size == 2,
              encoderInput == expectedEncoderInput,
              publisherInput.size == 1,
              publisherInput == expectedPublisherInput,
            )
          }

        val settings = MetricAgent.Settings(
          pollingInterval = 25.seconds,
          maxPublishingSize = 1000,
          snapshotQueueType = MetricAgent.QueueType.Dropping,
          snapshotQueueSize = 50,
        )

        elapasedTime("simple")(testCase)
          .provide(
            ZLayer.succeed(settings),
            MockMetricEncoder.mock[String] { case (p, ts) => Chunk(s"${p.metricKey}-$ts") },
            MockMetricPublisher.mock[String],
            MockMetricRegistry.mock(),
            MetricAgent.live[String],
          )
      }
    },
    test("Should partition based on 'batchMaxDelay' settings.") {
      check(genCounter, genGauge, genCounter) { case ((pair1, _), (pair2, _), (pair3, _)) =>
        val testCase =
          for {
            agent          <- ZIO.service[MetricAgent[String]]
            publisher      <- ZIO.service[MockMetricPublisher[String]]
            encoder        <- ZIO.service[MockMetricEncoder[String]]
            registry       <- ZIO.service[MockMetricRegistry]
            ts1            <- Clock.currentTime(TimeUnit.MILLISECONDS)
            ts2             = ts1 + 1000
            fiber          <- agent.runAgent
            _              <- registry.putMetric(pair1 -> ts1, pair2 -> ts2)
            _              <- TestClock.adjust(30.seconds)
            state0         <- publisher.state
            _              <- TestClock.adjust(30.seconds)
            state1         <- publisher.state
            _              <- registry.putMetric(pair3 -> ts2)
            _              <- TestClock.adjust(30.seconds)
            state2         <- publisher.state
            _              <- TestClock.adjust(30.seconds)
            state3         <- publisher.state
            _              <- fiber.interrupt
            publisherInput <- publisher.state

          } yield {

            val expectedState2 = Chunk(Chunk(s"${pair1.metricKey}-$ts1", s"${pair2.metricKey}-$ts2"))

            val expectedState3 =
              Chunk(Chunk(s"${pair1.metricKey}-$ts1", s"${pair2.metricKey}-$ts2"), Chunk(s"${pair3.metricKey}-$ts2"))

            assertTrue(
              state0.size == 0,
              state1.size == 1,
              state2.size == 1,
              state3.size == 2,
              state2 == expectedState2,
              state3 == expectedState3,
            )
          }

        val settings =
          MetricAgent
            .Settings(
              pollingInterval = 2.seconds,
              maxPublishingSize = 1000,
              snapshotQueueType = MetricAgent.QueueType.Dropping,
              snapshotQueueSize = 50,
            )

        elapasedTime("maxDelay")(testCase)
          .provide(
            ZLayer.succeed(settings),
            MockMetricEncoder.mock[String] { case (p, ts) => Chunk(s"${p.metricKey}-$ts") },
            MockMetricPublisher.mock[String],
            MockMetricRegistry.mock(),
            MetricAgent.live[String],
          )
      }
    } @@ ignore,
    test("Should partition based on 'maxPublishingSize' settings.") {
      check(genCounter, genGauge, genCounter) { case ((pair1, _), (pair2, _), (pair3, _)) =>
        val testCase =
          for {
            agent          <- ZIO.service[MetricAgent[String]]
            publisher      <- ZIO.service[MockMetricPublisher[String]]
            encoder        <- ZIO.service[MockMetricEncoder[String]]
            registry       <- ZIO.service[MockMetricRegistry]
            fiber          <- agent.runAgent
            ts1            <- Clock.currentTime(TimeUnit.MILLISECONDS)
            ts2             = ts1 + 1000
            _              <- registry.putMetric(pair1 -> ts1, pair2 -> ts2, pair3 -> ts2)
            _              <- TestClock.adjust(1.minute)
            _              <- fiber.interrupt
            publisherInput <- publisher.state

          } yield {

            val expectedPublisherInput =
              Chunk(Chunk(s"${pair1.metricKey}-$ts1", s"${pair2.metricKey}-$ts2"), Chunk(s"${pair3.metricKey}-$ts2"))

            assertTrue(
              publisherInput.size == 2,
              publisherInput == expectedPublisherInput,
            )
          }

        val settings = MetricAgent.Settings(
          pollingInterval = 5.seconds,
          maxPublishingSize = 2,
          snapshotQueueType = MetricAgent.QueueType.Dropping,
          snapshotQueueSize = 50,
        )

        elapasedTime("maxSize")(testCase)
          .provide(
            ZLayer.succeed(settings),
            MockMetricEncoder.mock[String] { case (p, ts) => Chunk(s"${p.metricKey}-$ts") },
            MockMetricPublisher.mock[String],
            MockMetricRegistry.mock(),
            MetricAgent.live[String],
          )
      }
    },
  ) @@ samples(1) @@ shrinks(0)

  private def perfTest(name: String, metricQty: Int, minPerf: Duration) =
    test(s"$name.  Execution time <= ${minPerf.toMillis}ms.") {

      val pairs = (1 to metricQty).map { i =>
        val state = MetricState.Counter(i.toDouble)
        MetricPair.unsafeMake(MetricKey.counter(s"$i-conuter"), state)
      }.toSeq

      val setUp =
        for {
          registry <- ZIO.service[MockMetricRegistry]
          ts1      <- Clock.currentTime(TimeUnit.MILLISECONDS)
          _        <- registry.putMetric(pairs.map(_ -> ts1): _*)
        } yield assertTrue(
          true,
        )

      val pgm = for {
        agent              <- ZIO.service[MetricAgent[String]]
        publisher          <- ZIO.service[MockMetricPublisher[String]]
        publishingComplete <- Promise.make[Nothing, Unit]
        fiber              <- agent.runAgent
        _                  <- ZStream.unit
                                .mapZIO { _ =>
                                  publisher.state.flatMap { c =>
                                    (
                                      if (c.flatten.size == metricQty) publishingComplete.succeed(())
                                      else ZIO.unit,
                                    )

                                  }

                                }
                                .forever
                                .runDrain
                                .fork
        _                  <- publishingComplete.await
        _                  <- fiber.interrupt
      } yield ()

      val testCase = for {
        _         <- setUp
        _         <- elapasedTime(s"perf test: $name")(pgm)
        publisher <- ZIO.service[MockMetricPublisher[String]]
        state     <- publisher.state

      } yield assertTrue(
        state.flatten.size == metricQty,
      )

      val settings = MetricAgent.Settings(
        pollingInterval = 30.millis,
        maxPublishingSize = 1000,
        snapshotQueueType = MetricAgent.QueueType.Dropping,
        snapshotQueueSize = 50,
      )

      testCase
        .provide(
          ZLayer.succeed(settings),
          MockMetricEncoder.mock[String] { case (p, ts) => Chunk(s"${p.metricKey}-$ts") },
          MockMetricPublisher.mock[String],
          MockMetricRegistry.mock(),
          MetricAgent.live[String],
        )
    } @@ withLiveClock @@ timeout(minPerf) @@ flaky @@ shrinks(0)

  private val perfSuite = suite("Performance")(
    perfTest("1k Metrics", 1000, 75.millis),
    perfTest("3k Metrics", 3000, 125.millis),
    perfTest("10k Metrics", 10000, 250.millis),
    perfTest("20k Metrics", 20000, 250.millis),
  )

}
