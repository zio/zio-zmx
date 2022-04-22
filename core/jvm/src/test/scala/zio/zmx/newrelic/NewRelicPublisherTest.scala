package zio.zmx.newrelic

import java.time.Instant

import zio._
import zio.json.ast.Json
import zio.test._
import zio.zmx._

import zhttp.service._

object NewRelicPublisherTest extends ZIOSpecDefault {

  def spec = suite("NewRelicPublisherTest")(
    test("Simple request to NewRelic endpoint") {
      val ts = Instant.now().toEpochMilli()

      val testData: Json = Json.Obj(
        "name"      -> Json.Str("NewRelicPublisherTest.guage"),
        "type"      -> Json.Str("gauge"),
        "value"     -> Json.Num(42.42),
        "timestamp" -> Json.Num(ts),
      )

      val jsons = Chunk(testData)

      val settings = ZLayer {
        for {
          apiKey <- ZIO.fromOption(Option(java.lang.System.getenv("NEW_RELIC_API_KEY"))).mapError { case _ =>
                      ZIO.fail(
                        new IllegalArgumentException(
                          "To run new relic publisher tests, you need to define the `NEW_RELIC_API_KEY` environment variable.",
                        ),
                      )
                    }

        } yield (NewRelicPublisher
          .Settings(apiKey, "https://metric-api.newrelic.com/metric/v1"))
      }

      val pgm = for {

        result <- MetricPublisher.publish[Json](jsons)
        _      <- Console.printLine(result)

      } yield assertTrue(
        result == MetricPublisher.Result.Success
      )

      pgm.provide(
        settings,
        ChannelFactory.nio,
        EventLoopGroup.nio(),
        MetricPublisher.newRelic,
      )

    },
  )

}
