package zio.zmx.newrelic

import zio._
import zio.test._
import zio.zmx.Generators
import zio.zmx.Mocks._

import TestAspect._

object NewRelicPublisherSpec extends ZIOSpecDefault with Generators {

  def spec = suite("NewRelicPublisherSpec")(
    test("Should publish a MetricPair.") {
      check(genCounter) { case (pair, _) =>
        val testCase = for {
          publisher   <- ZIO.service[NewRelicPublisher]
          encoder     <- ZIO.service[MockNewRelicEncoder]
          _           <- ZIO.attempt(publisher.unsafePublish(pair)).absorb
          fiber       <- publisher.runPublisher
          _           <- TestClock.adjust(1.minute)
          _           <- fiber.interrupt
          encoderInput = encoder.state

        } yield {
          val nonEmptyPair = encoderInput.head._1.head
          val shouldBeEmpty = encoderInput.tail.flatMap {
            case (pairs, _) => pairs
          }

          assertTrue(
            nonEmptyPair == pair,
            shouldBeEmpty.isEmpty
          )
        }

        testCase.provide(
          MockNewRelicClient.mock,
          MockNewRelicEncoder.mock,
          NewRelicPublisher.live,
        )
      }
    } @@ samples(1),
  )

}
