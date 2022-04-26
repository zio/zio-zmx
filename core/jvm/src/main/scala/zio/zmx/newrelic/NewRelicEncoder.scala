/*
 * Copyright 2022 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.zmx.newrelic

import java.time.Instant

import zio._
import zio.json.ast._
import zio.metrics._
import zio.metrics.MetricState._
import zio.zmx._
import zio.zmx.MetricEvent._

import NewRelicEncoder._

object NewRelicEncoder {

  private[zmx] val frequencyTagName = "zmx.frequency.name"

  final case class Settings(defaultIntervalMillis: Long)

  val make: Settings => MetricEventEncoder[Json] = NewRelicEncoder(_)

}

final case class NewRelicEncoder(config: Settings) extends MetricEventEncoder[Json] {

  override def encode(event: MetricEvent): ZIO[Any, Throwable, Chunk[Json]] =
    event match {
      case New(key, state, timestamp)                        => encodeMetric(key, None, state, timestamp)
      case Unchanged(_, _, _)                                => ZIO.succeed(Chunk.empty)
      case Updated(metricKey, oldState, newState, timestamp) =>
        encodeMetric(metricKey, Some(oldState), newState, timestamp)
    }

  private def encodeMetric(
    metricKey: MetricKey.Untyped,
    oldMetric: Option[MetricState.Untyped],
    newMetric: MetricState.Untyped,
    timestamp: Instant,
  ): ZIO[Any, Throwable, Chunk[Json]] =
    ZIO.succeed {
      (newMetric, oldMetric) match {
        case (Frequency(newOccurrences), oldFrequency)            =>
          val oldOccurrences = oldFrequency.asInstanceOf[Option[Frequency]].fold(Map.empty[String, Long])(_.occurrences)
          encodeFrequency(
            oldOccurrences,
            newOccurrences,
            metricKey,
            config.defaultIntervalMillis,
            timestamp,
          )
        case (Summary(error, quantiles, count, min, max, sum), _) =>
          encodeSummary(
            error,
            quantiles,
            count,
            min,
            max,
            sum,
            metricKey,
            config.defaultIntervalMillis,
            timestamp,
          )
        case (Counter(count), oldCounter)                         =>
          Chunk(
            encodeCounter(
              oldCounter.asInstanceOf[Option[Counter]].fold(0.0)(_.count),
              count,
              metricKey,
              config.defaultIntervalMillis,
              timestamp,
              Set(makeZmxTypeTag("Counter")),
            ),
          )
        case (Histogram(buckets, count, min, max, sum), _)        =>
          encodeHistogram(
            buckets,
            count,
            min,
            max,
            sum,
            metricKey,
            config.defaultIntervalMillis,
            timestamp,
          )
        case (Gauge(value), _)                                    =>
          Chunk(encodeGauge(value, metricKey, timestamp, Set(makeZmxTypeTag("Gauge"))))
      }
    }

  private[zmx] def encodeAttributes(labels: Set[MetricLabel], additionalAttributes: Set[(String, Json)]): Json =
    Json.Obj(
      "attributes" -> Json.Obj(Chunk.fromIterable(labels.map { case MetricLabel(name, value) =>
        sanitzeLabelName(name) -> Json.Str(value)
      } ++ additionalAttributes.map { case (name, value) =>
        sanitzeLabelName(name) -> value
      })),
    )

  private[zmx] def encodeCommon(name: String, newRelicMetricType: String, timestamp: Instant): Json.Obj =
    Json.Obj(
      "name"      -> Json.Str(name),
      "type"      -> Json.Str(newRelicMetricType),
      "timestamp" -> Json.Num(timestamp.toEpochMilli()),
    )

  private[zmx] def encodeCounter(
    oldCount: Double,
    newCount: Double,
    key: MetricKey.Untyped,
    interval: Long,
    timestamp: Instant,
    additionalAttributes: Set[(String, Json)],
  ): Json = {

    val count = (oldCount - newCount).abs // oldCount.fold(newCount)(_ - newCount).abs
    encodeCommon(key.name, "count", timestamp) merge Json.Obj(
      "value"       -> Json.Num(count),
      "interval.ms" -> Json.Num(interval),
    ) merge encodeAttributes(key.tags, additionalAttributes)
  }

  private[zmx] def encodeFrequency(
    oldOccurrences: Map[String, Long],
    newOccurrences: Map[String, Long],
    key: MetricKey.Untyped,
    interval: Long,
    timestamp: Instant,
  ): Chunk[Json] = {
    val grouped = (oldOccurrences.toList ++ newOccurrences.toList).groupBy(_._1)
    val deltas  = grouped.map {
      case (key, oldCount :: newCount :: Nil) => (key, (oldCount._2, newCount._2))
      case (key, count :: Nil)                => (key, (0L, count._2))
      case (key, _)                           => (key, (0L, 0L))
    }

    Chunk.fromIterable(deltas.map { case (frequencyName, (oldCount, newCount)) =>
      val tags: Set[(String, Json)] = Set(frequencyTagName -> Json.Str(frequencyName), makeZmxTypeTag("Frequency"))
      encodeCounter(oldCount.toDouble, newCount.toDouble, key, interval, timestamp, tags)
    })

  }

  private[zmx] def encodeGauge(
    value: Double,
    key: MetricKey.Untyped,
    timestamp: Instant,
    additionalAttributes: Set[(String, Json)],
  ): Json =
    encodeCommon(key.name, "gauge", timestamp) merge Json.Obj(
      "value" -> Json.Num(value),
    ) merge encodeAttributes(key.tags, additionalAttributes)

  private[zmx] def encodeHistogram(
    buckets: Chunk[(Double, Long)],
    count: Long,
    min: Double,
    max: Double,
    sum: Double,
    key: MetricKey.Untyped,
    interval: Long,
    timestamp: Instant,
  ): Chunk[Json] = {

    val zmxType = makeZmxTypeTag("Histogram")

    val histogram = encodeCommon(key.name, "summary", timestamp) merge
      makeNewRelicSummary(count, sum, interval, min, max) merge
      encodeAttributes(key.tags, Set(zmxType))

    Chunk(histogram)
  }

  private[zmx] def encodeSummary(
    error: Double,
    quantiles: Chunk[(Double, Option[Double])],
    count: Long,
    min: Double,
    max: Double,
    sum: Double,
    key: MetricKey.Untyped,
    interval: Long,
    timestamp: Instant,
  ): Chunk[Json] = {

    val zmxType = makeZmxTypeTag("Summary")

    val summary = encodeCommon(key.name, "summary", timestamp) merge
      makeNewRelicSummary(count, sum, interval, min, max) merge
      encodeAttributes(key.tags, Set(zmxType, "zmx.error.margin" -> Json.Num(error)))

    Chunk(summary)
  }

  private[zmx] def makeNewRelicSummary(
    count: Long,
    sum: Double,
    intervalInMillis: Long,
    min: Double,
    max: Double,
  ): Json.Obj = Json.Obj(
    "count"       -> Json.Num(count),
    "sum"         -> Json.Num(sum),
    "interval.ms" -> Json.Num(intervalInMillis),
    "min"         -> Json.Num(min),
    "max"         -> Json.Num(max),
  )

  private[zmx] def makeZmxTypeTag(zmxType: String): (String, Json) = "zmx.type" -> Json.Str(zmxType)

  private[zmx] def reservedWords = Chunk(
    "ago",
    "and",
    "as",
    "auto",
    "begin",
    "begintime",
    "compare",
    "day",
    "days",
    "end",
    "endtime",
    "explain",
    "facet",
    "from",
    "hour",
    "hours",
    "in",
    "is",
    "like",
    "limit",
    "minute",
    "minutes",
    "month",
    "months",
    "not",
    "null",
    "offset",
    "or",
    "raw",
    "second",
    "seconds",
    "select",
    "since",
    "timeseries",
    "until",
    "week",
    "weeks",
    "where",
    "with",
  )

  private[zmx] def sanitzeLabelName(name: String): String =
    if (!name.startsWith("zmx") && reservedWords.contains(name.toLowerCase())) s"`$name`" else name
}
