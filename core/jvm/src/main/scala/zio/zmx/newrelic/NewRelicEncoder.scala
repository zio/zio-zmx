package zio.zmx.newrelic

import zio._
import zio.json.ast._
import zio.metrics._
import zio.metrics.MetricState._

import NewRelicEncoder._
import zio.zmx.MetricEncoder


object NewRelicEncoder {

  private[zmx] val frequencyTagName = "zmx.frequency.name"

  def make(config: NewRelicConfig): MetricEncoder[Json] = NewRelicEncoder(config)

  def live = ZIO.service[NewRelicConfig].map(make).toLayer
}

final case class NewRelicEncoder(config: NewRelicConfig) extends MetricEncoder[Json] {

  // def encodeMetrics(metrics: Chunk[MetricPair.Untyped], timestamp: Long): Chunk[Json] =
  //   metrics.flatMap(encodeMetric(_, config, timestamp))

  private[zmx] def encodeAttributes(labels: Set[MetricLabel], additionalAttributes: Set[(String, Json)]): Json =
    Json.Obj(
      "attributes" -> Json.Obj(Chunk.fromIterable(labels.map { case MetricLabel(name, value) =>
        sanitzeLabelName(name) -> Json.Str(value)
      } ++ additionalAttributes.map { case (name, value) =>
        sanitzeLabelName(name) -> value
      })),
    )

  private[zmx] def encodeCommon(name: String, newRelicMetricType: String, timestamp: Long): Json.Obj =
    Json.Obj(
      "name"      -> Json.Str(name),
      "type"      -> Json.Str(newRelicMetricType),
      "timestamp" -> Json.Num(timestamp),
    )

  private[zmx] def encodeCounter(
    count: Double,
    key: MetricKey.Untyped,
    interval: Long,
    timestamp: Long,
    additionalAttributes: Set[(String, Json)],
  ): Json =
    encodeCommon(key.name, "counter", timestamp) merge Json.Obj(
      "count"       -> Json.Num(count),
      "interval.ms" -> Json.Num(interval),
    ) merge encodeAttributes(key.tags, additionalAttributes)

  private[zmx] def encodeFrequency(
    occurrences: Map[String, Long],
    key: MetricKey.Untyped,
    interval: Long,
    timestamp: Long,
  ): Chunk[Json] =
    Chunk.fromIterable(occurrences.map { case (frequencyName, count) =>
      val tags: Set[(String, Json)] = Set(frequencyTagName -> Json.Str(frequencyName), makeZmxTypeTag("Frequency"))
      encodeCounter(count.toDouble, key, interval, timestamp, tags)
    })

  private[zmx] def encodeGauge(
    value: Double,
    key: MetricKey.Untyped,
    timestamp: Long,
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
    timestamp: Long,
  ): Chunk[Json] = {

    val zmxType = makeZmxTypeTag("Histogram")

    val encodedbuckets = buckets.map { case (bucket, value) =>
      val name          = s"${key.name}.bucket.$bucket"
      val addtionalTags =
        Set(
          s"zmx.histogram.name" -> Json.Str(
            key.name,
          ), // Reference to the histogram metric to which this bucket belongs.
          s"zmx.histogram.bucket" -> Json.Num(bucket),
          zmxType,
        )
      encodeGauge(value.toDouble, MetricKey.gauge(name), timestamp, addtionalTags)
    }

    val histogram = encodeCommon(key.name, "summary", timestamp) merge
      makeNewRelicSummary(count, sum, interval, min, max) merge
      encodeAttributes(key.tags, Set(zmxType))

    histogram +: encodedbuckets
  }

  def encodeMetric(
    metric: MetricPair.Untyped,
    timestamp: Long,
  ): ZIO[Any, Throwable, Chunk[Json]] =
    ZIO.succeed {
      metric.metricState match {
        case Frequency(occurrences)                          =>
          encodeFrequency(occurrences, metric.metricKey, config.defaultIntervalMillis, timestamp)
        case Summary(error, quantiles, count, min, max, sum) =>
          encodeSummary(
            error,
            quantiles,
            count,
            min,
            max,
            sum,
            metric.metricKey,
            config.defaultIntervalMillis,
            timestamp,
          )
        case Counter(count)                                  =>
          Chunk(
            encodeCounter(
              count,
              metric.metricKey,
              config.defaultIntervalMillis,
              timestamp,
              Set(makeZmxTypeTag("Counter")),
            ),
          )
        case Histogram(buckets, count, min, max, sum)        =>
          encodeHistogram(buckets, count, min, max, sum, metric.metricKey, config.defaultIntervalMillis, timestamp)
        case Gauge(value)                                    =>
          Chunk(encodeGauge(value, metric.metricKey, timestamp, Set(makeZmxTypeTag("Gauge"))))
      }
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
    timestamp: Long,
  ): Chunk[Json] = {

    val zmxType = makeZmxTypeTag("Summary")

    val encodedQuantiles = quantiles.map { case (quantile, value) =>
      val name          = s"${key.name}.quantile.$quantile"
      val addtionalTags =
        Set(
          s"zmx.summary.name"     -> Json.Str(key.name), // Reference to the summary metric to which this quantile belongs.
          s"zmx.summary.quantile" -> Json.Num(quantile),
          zmxType,
        )
      encodeGauge(value.getOrElse(0.0), MetricKey.gauge(name), timestamp, addtionalTags)
    }

    val summary = encodeCommon(key.name, "summary", timestamp) merge
      makeNewRelicSummary(count, sum, interval, min, max) merge
      encodeAttributes(key.tags, Set(zmxType, "zmx.error.margin" -> Json.Num(error)))

    summary +: encodedQuantiles
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
