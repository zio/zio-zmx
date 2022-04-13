package zio.zmx

import java.util.concurrent.TimeUnit

import zio._
import zio.test._

import TestAspect._

object MetricAgentSpec extends ZIOSpecDefault with Generators {
  import Mocks._

  val settings = MetricAgent.Settings(5.seconds, 1000, 1.minute)

  def spec = suite("MetricAgentSpec")(
    test("Should publish a MetricPair.") {
      check(genCounter) { case (pair, _) =>
        val testCase = for {
          agent          <- ZIO.service[MetricAgent[String]]
          publisher      <- ZIO.service[MockMetricPublisher[String]]
          encoder        <- ZIO.service[MockMetricEncoder[String]]
          registry       <- ZIO.service[MockMetricRegistry]
          now            <- Clock.currentTime(TimeUnit.MILLISECONDS)
          _               <- registry.putMetric(pair, now)
          fiber          <- agent.runAgent
          _              <- TestClock.adjust(71.seconds)
          _              <- fiber.interrupt
          encoderInput   <- encoder.state
          publisherInput <- publisher.state

        } yield {
          // val nonEmptyPair  = encoderInput.head._1.head
          // val shouldBeEmpty = encoderInput.tail.flatMap { case (pairs, _) =>
          //   pairs
          // }
          // println(encoderInput)

          assertTrue(
            true,
            encoderInput.size == 1,
            publisherInput.size == 1
            // nonEmptyPair == pair,
            // shouldBeEmpty.isEmpty,
          )
        }

        testCase.provide(
          ZLayer.succeed(settings),
          MockMetricEncoder.mock[String]({case (p, t) => Chunk(p.toString)}),
          MockMetricPublisher.mock[String],
          MockMetricRegistry.mock(),
          MetricAgent.live[String],
        )
      }
    } @@ samples(1) @@ shrinks(0),
  )

}
