package zio.metrics.connectors.newrelic

import java.time.Instant

import zio._
import zio.json.ast.Json
import zio.metrics.connectors._
import zio.stream.ZStream
import zio.test._

import TestAspect._
import testing._
import zhttp.service._

object NewRelicPublisherSpec extends ZIOSpecDefault with Generators {

  def spec = suite("NewRelicPublisherTest")(
    test("Simple request to NewRelic endpoint") {
      check(genPosDouble) { value =>
        val ts = Instant.now().toEpochMilli()

        val testData: Json = Json.Obj(
          "name"      -> Json.Str("NewRelicPublisherTest.gauge"),
          "type"      -> Json.Str("gauge"),
          "value"     -> Json.Num(value),
          "timestamp" -> Json.Num(ts),
        )

        val jsons = Chunk(testData)

        sendAndVerify(jsons)

      }

    },
    test("multiple metrics to NewRelic endpoint") {
      check(Gen.listOfN(5)(genCounterNamed("NewRelicPublisherTest.counters", 2, 5))) { counters =>
        val encoder = NewRelicEncoder(Instant.now())

        val stream = ZStream
          .fromIterable(counters.map(_._1))
          .mapZIO { e =>
            for {
              _     <- ZIO.unit.delay(1.second)
              now   <- Clock.instant
              jsons <- encoder.encode(MetricEvent.New(e.metricKey, e.metricState, now))

            } yield jsons

          }

        for {
          jsons  <- stream.flattenChunks.runCollect
          result <- sendAndVerify(jsons)
        } yield result

      }
    },
  ) @@ samples(1) @@ withLiveClock @@ withLiveRandom @@ ignoreIf(
    !NewRelicPublisher.Settings.envvars.apiKey.isDefined,
    "To run the NewRelicPublisherSpec, you need to define the `NEW_RELIC_API_KEY` environment variable.",
  )

  private def sendAndVerify(jsons: Iterable[Json]) = {
    val settings = ZLayer {
      for {
        apiKey <- ZIO.fromOption(Option(java.lang.System.getenv("NEW_RELIC_API_KEY"))).mapError { case _ =>
                    ZIO.fail(
                      new IllegalArgumentException(
                        "To run the NewRelicPublisherSpec, you need to define the `NEW_RELIC_API_KEY` environment variable.",
                      ),
                    )
                  }

      } yield (NewRelicPublisher
        .Settings(apiKey, "https://metric-api.newrelic.com/metric/v1", 5, 1.second))
    }

    val pgm = for {

      result <- MetricPublisher.publish[Json](jsons)
      _      <- Console.printLine(result)

    } yield assertTrue(
      result == MetricPublisher.Result.Success,
    )

    pgm.provide(
      settings,
      ChannelFactory.nio,
      EventLoopGroup.nio(),
      MetricPublisher.newRelic,
    )

  }

}
