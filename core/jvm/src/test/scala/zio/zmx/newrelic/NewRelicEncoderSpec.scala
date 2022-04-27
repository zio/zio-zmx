package zio.zmx.newrelic

import java.time.Instant

import zio._
import zio.json.ast._
import zio.metrics._
import zio.test._
import zio.test.Assertion._
import zio.zmx.Generators
import zio.zmx.JsonAssertions._
import zio.zmx.MetricEvent._

object NewRelicEncoderSpec extends ZIOSpecDefault with Generators {

  val startedAtInstant = Instant.now()
  val startedAt        = startedAtInstant.toEpochMilli
  val encoder          = NewRelicEncoder(startedAtInstant)

  def spec = suite("NewRelicEventEncoderSpec")(
    newEventSuite,
    updatedEventSuite,
    unchangedEventSuite,
  )

  private val newEventSuite = suite("Event.New")(
    test("should encode a Counter to the expected JSON format.") {
      check(genCounter) { case (pair, state) =>
        val timestamp  = Instant.now
        val jsonChunks = encoder.encode(New(pair.metricKey, pair.metricState, timestamp))

        jsonChunks.map { jsonChunks =>
          assert(jsonChunks.size)(equalTo(1)) &&
          assert(jsonChunks.head)(
            NewRelicAssertions.hasCommonFields(pair.metricKey.name, "count", timestamp.toEpochMilli()),
          ) &&
          assert(jsonChunks.head)(hasFieldWithValue("value", Json.Num(state.count))) &&
          assert(jsonChunks.head)(hasFieldWithValue("interval.ms", Json.Num(timestamp.toEpochMilli - startedAt))) &&
          assert(jsonChunks.head)(NewRelicAssertions.hasAttribute("zmx.type", Json.Str("Counter")))
        }

      }
    },
    test("should encode a Gauge to the expected JSON format.") {
      check(genGauge) { case (pair, state) =>
        val timestamp  = Instant.now
        val jsonChunks = encoder.encode(New(pair.metricKey, pair.metricState, timestamp))

        jsonChunks.map { jsonChunks =>
          assert(jsonChunks.size)(equalTo(1)) &&
          assert(jsonChunks.head)(
            NewRelicAssertions.hasCommonFields(pair.metricKey.name, "gauge", timestamp.toEpochMilli()),
          ) &&
          assert(jsonChunks.head)(hasFieldWithValue("value", Json.Num(state.value))) &&
          assert(jsonChunks.head)(NewRelicAssertions.hasAttribute("zmx.type", Json.Str("Gauge")))
        }
      }
    },
    test("should encode a Frequency to the expected JSON format.") {
      Ref
        .make(Set.empty[String])
        .flatMap(ref =>
          check(genFrequency(3, ref)) { case (pair, state) =>
            val timestamp   = Instant.now
            val jsonChunks  = encoder.encode(New(pair.metricKey, pair.metricState, timestamp))
            val occurrences = state.occurrences.toVector

            jsonChunks.map { jsonChunks =>
              def makeCounterAssertion(index: Int) = NewRelicAssertions.hasCounter(
                pair.metricKey.name,
                occurrences(index)._2,
                occurrences(index)._1,
                timestamp.toEpochMilli(),
                timestamp.toEpochMilli - startedAt,
              )

              assert(jsonChunks.size)(equalTo(3)) &&
              assert(jsonChunks)(makeCounterAssertion(0)) &&
              assert(jsonChunks)(makeCounterAssertion(1)) &&
              assert(jsonChunks)(makeCounterAssertion(2))
            }
          },
        )
    },
  )

  private val updatedEventSuite = suite("Event.Changed")(
    test("should encode a Counter to the expected JSON format.") {
      check(genCounter, genCounter) { case ((pair, oldState), (_, newState)) =>
        val timestamp  = Instant.now
        val jsonChunks = encoder.encode(Updated(pair.metricKey, oldState, newState, timestamp))

        val expectedCount = (oldState.count - newState.count).abs

        jsonChunks.map { jsonChunks =>
          assert(jsonChunks.size)(equalTo(1)) &&
          assert(jsonChunks.head)(
            NewRelicAssertions.hasCommonFields(pair.metricKey.name, "count", timestamp.toEpochMilli()),
          ) &&
          assert(jsonChunks.head)(hasFieldWithValue("value", Json.Num(expectedCount))) &&
          assert(jsonChunks.head)(hasFieldWithValue("interval.ms", Json.Num(timestamp.toEpochMilli - startedAt))) &&
          assert(jsonChunks.head)(NewRelicAssertions.hasAttribute("zmx.type", Json.Str("Counter")))
        }

      }
    },
    test("should encode a Gauge to the expected JSON format.") {
      check(genGauge, genGauge) { case ((pair, oldState), (_, newState)) =>
        val timestamp  = Instant.now
        val jsonChunks = encoder.encode(Updated(pair.metricKey, oldState, newState, timestamp))

        jsonChunks.map { jsonChunks =>
          assert(jsonChunks.size)(equalTo(1)) &&
          assert(jsonChunks.head)(
            NewRelicAssertions.hasCommonFields(pair.metricKey.name, "gauge", timestamp.toEpochMilli()),
          ) &&
          assert(jsonChunks.head)(hasFieldWithValue("value", Json.Num(newState.value))) &&
          assert(jsonChunks.head)(NewRelicAssertions.hasAttribute("zmx.type", Json.Str("Gauge")))
        }
      }
    },
    test("should encode a Frequency to the expected JSON format.") {
      val oldOcc      = Map("foo" -> 1L, "bar" -> 2L, "baz" -> 42L)
      val newOcc      = Map("foo" -> 3L, "bar" -> 5L, "blorp" -> 8L)
      val expectedOcc = Map("foo" -> 2L, "bar" -> 3L, "baz" -> 42L, "blorp" -> 8L)

      val key      = MetricKey.frequency("frequency")
      val oldState = MetricState.Frequency(oldOcc)
      val newState = MetricState.Frequency(newOcc)

      val timestamp  = Instant.now
      val jsonChunks = encoder.encode(Updated(key, oldState, newState, timestamp))

      val occurrences = expectedOcc.toVector

      def makeCounterAssertion(index: Int) = NewRelicAssertions.hasCounter(
        key.name,
        occurrences(index)._2,
        occurrences(index)._1,
        timestamp.toEpochMilli(),
        timestamp.toEpochMilli - startedAt,
      )

      jsonChunks.map { jsonChunk =>
        assertTrue(
          jsonChunk.size == 4,
        ) && assert(jsonChunk)(makeCounterAssertion(0)) &&
        assert(jsonChunk)(makeCounterAssertion(1)) &&
        assert(jsonChunk)(makeCounterAssertion(2)) &&
        assert(jsonChunk)(makeCounterAssertion(3))

      }
    },
  )

  private val unchangedEventSuite = suite("Event.Unchanged")(
    test("should never produce ouput (except for Gauges).") {
      // Note, that Gauges are always encoded
      check(Gen.oneOf(genCounter, genGauge, genFrequency1, genHistogram)) { case (pair, _) =>
        encoder.encode(Unchanged(pair.metricKey, pair.metricState, Instant.now())).map { chunk =>
          assertTrue(
            chunk.isEmpty != pair.metricState.isInstanceOf[MetricState.Gauge],
          )
        }
      }
    },
  )

}
