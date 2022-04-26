package zio.zmx.attic

// import java.time.Instant

// import zio._
// import zio.json.ast._
import zio.test._
// import zio.test.Assertion._
import zio.zmx.Generators
// import zio.zmx.JsonAssertions._

object NewRelicEncoderSpec extends ZIOSpecDefault with Generators {

  // val config  = NewRelicEncoder.Settings(10000)
  // val encoder = NewRelicEncoder.make(config)

  def spec = suite("NewRelicEncoderSpec")(
    // test("Should encode a Counter to the expected JSON format.") {
    //   check(genCounter) { case (pair, state) =>
    //     val timestamp  = Instant.now.toEpochMilli()
    //     val jsonChunks = encoder.encodeMetric(pair, timestamp)

    //     jsonChunks.map { jsonChunks =>
    //       assert(jsonChunks.size)(equalTo(1)) &&
    //       assert(jsonChunks.head)(NewRelicAssertions.hasCommonFields(pair.metricKey.name, "counter", timestamp)) &&
    //       assert(jsonChunks.head)(hasFieldWithValue("count", Json.Num(state.count))) &&
    //       assert(jsonChunks.head)(hasFieldWithValue("interval.ms", Json.Num(config.defaultIntervalMillis))) &&
    //       assert(jsonChunks.head)(NewRelicAssertions.hasAttribute("zmx.type", Json.Str("Counter")))
    //     }

    //   }
    // },
    // test("Should encode a Gauge to the expected JSON format.") {
    //   check(genGauge) { case (pair, state) =>
    //     val timestamp  = Instant.now.toEpochMilli()
    //     val jsonChunks = encoder.encodeMetric(pair, timestamp)

    //     jsonChunks.map { jsonChunks =>
    //       assert(jsonChunks.size)(equalTo(1)) &&
    //       assert(jsonChunks.head)(NewRelicAssertions.hasCommonFields(pair.metricKey.name, "gauge", timestamp)) &&
    //       assert(jsonChunks.head)(hasFieldWithValue("value", Json.Num(state.value))) &&
    //       assert(jsonChunks.head)(NewRelicAssertions.hasAttribute("zmx.type", Json.Str("Gauge")))
    //     }
    //   }
    // },
    // test("Should encode a Frequency to the expected JSON format.") {
    //   Ref
    //     .make(Set.empty[String])
    //     .flatMap(ref =>
    //       check(genFrequency(3, ref)) { case (pair, state) =>
    //         val timestamp   = Instant.now.toEpochMilli()
    //         val jsonChunks  = encoder.encodeMetric(pair, timestamp)
    //         // val (rootJsonArr, metricsArr) = breakDownJson(jsonChunks)
    //         val occurrences = state.occurrences.toVector

    //         jsonChunks.map { jsonChunks =>
    //           def makeCounterAssertion(index: Int) = NewRelicAssertions.hasCounter(
    //             pair.metricKey.name,
    //             occurrences(index)._2,
    //             occurrences(index)._1,
    //             timestamp,
    //             config.defaultIntervalMillis,
    //           )

    //           assert(jsonChunks.size)(equalTo(3)) &&
    //           assert(jsonChunks)(makeCounterAssertion(0)) &&
    //           assert(jsonChunks)(makeCounterAssertion(1)) &&
    //           assert(jsonChunks)(makeCounterAssertion(2))
    //         }
    //       },
    //     )
    // },
  ) @@ TestAspect.ignore

  // def breakDownJson(chunk: Chunk[Json]) = {
  //   val Json.Arr(rootJsonArr)       = chunk.head
  //   val Json.Obj(metricsJsonFields) = rootJsonArr.head
  //   val Json.Arr(metricsArr)        = metricsJsonFields.find(_._1 == "metrics").get._2
  //   rootJsonArr -> metricsArr
  // }

}
