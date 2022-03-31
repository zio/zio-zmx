package zio.zmx.newrelic

import java.time.Instant

import zio._
import zio.Chunk
import zio.json.ast._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.zmx.Generators
import zio.zmx.JsonAssertions._

object NewRelicEncoderSpec extends DefaultRunnableSpec with Generators {

  val config = NewRelicConfig(10000)

  def spec = suite("NewRelicEncoderSpec")(
    test("Should encode a Counter to the expected JSON format.") {
      check(genCounter) { case (pair, state) =>
        val pairs     = Chunk(pair)
        val timestamp = Instant.now.toEpochMilli()

        val jsonChunks = NewRelicEncoder.encodeMetrics(pairs, config, timestamp)

        val Json.Arr(rootJsonArr)       = jsonChunks.head
        val Json.Obj(metricsJsonFields) = rootJsonArr.head

        val Json.Arr(metricsObj) = metricsJsonFields.find(_._1 == "metrics").get._2

        assert(jsonChunks.size)(equalTo(1)) &&
        assert(rootJsonArr.size)(equalTo(1)) &&
        assert(metricsJsonFields.size)(equalTo(1)) &&
        assert(metricsObj.head)(NewRelicAssertions.hasCommonFields(pair.metricKey.name, "counter", timestamp)) &&
        assert(metricsObj.head)(hasFieldWithValue("count", Json.Num(state.count))) &&
        assert(metricsObj.head)(hasFieldWithValue("interval.ms", Json.Num(config.defaultIntervalMillis))) &&
        assert(metricsObj.head)(NewRelicAssertions.hasAttribute("zmx.type", Json.Str("Counter")))

      }
    },
    test("Should encode a Gauge to the expected JSON format.") {
      check(genGauge) { case (pair, state) =>
        val pairs     = Chunk(pair)
        val timestamp = Instant.now.toEpochMilli()

        val jsonChunks = NewRelicEncoder.encodeMetrics(pairs, config, timestamp)

        val Json.Arr(rootJsonArr)       = jsonChunks.head
        val Json.Obj(metricsJsonFields) = rootJsonArr.head

        val Json.Arr(metricsObj) = metricsJsonFields.find(_._1 == "metrics").get._2

        assert(jsonChunks.size)(equalTo(1)) &&
        assert(rootJsonArr.size)(equalTo(1)) &&
        assert(metricsJsonFields.size)(equalTo(1)) &&
        assert(metricsObj.head)(NewRelicAssertions.hasCommonFields(pair.metricKey.name, "gauge", timestamp)) &&
        assert(metricsObj.head)(hasFieldWithValue("value", Json.Num(state.value))) &&
        assert(metricsObj.head)(NewRelicAssertions.hasAttribute("zmx.type", Json.Str("Gauge")))
      }
    },
    test("Should encode a Frequency to the expected JSON format.") {
      Ref
        .make(Set.empty[String])
        .flatMap(ref =>
          check(genFrequency(3, ref)) { case (pair, state) =>
            val pairs     = Chunk(pair)
            val timestamp = Instant.now.toEpochMilli()

            val jsonChunks = NewRelicEncoder.encodeMetrics(pairs, config, timestamp)

            val ra @ Json.Arr(rootJsonArr)  = jsonChunks.head
            val Json.Obj(metricsJsonFields) = rootJsonArr.head

            val Json.Arr(metricsArr) = metricsJsonFields.find(_._1 == "metrics").get._2

            val occurrences = state.occurrences.toVector

            def makeCounterAssertion(index: Int) = NewRelicAssertions.hasCounter(
              pair.metricKey.name,
              occurrences(index)._2,
              occurrences(index)._1,
              timestamp,
              config.defaultIntervalMillis,
            )

            assert(jsonChunks.size)(equalTo(1)) &&
            assert(rootJsonArr.size)(equalTo(1)) &&
            assert(metricsArr.size)(equalTo(3)) &&
            assert(metricsArr)(makeCounterAssertion(0)) &&
            assert(metricsArr)(makeCounterAssertion(1)) &&
            assert(metricsArr)(makeCounterAssertion(2))

          }
        )
    },
  )

}
