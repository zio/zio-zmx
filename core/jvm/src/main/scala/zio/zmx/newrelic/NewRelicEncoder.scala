package zio.zmx.newrelic

import zio.Chunk
import zio.json.ast._
import zio.metrics._
import zio.metrics.MetricState._

object NewRelicEncoder {

  def encodeMetrics(metrics: Iterable[MetricPair.Untyped], config: NewRelicConfig, timestamp: Long): Chunk[Json] =
    Chunk.fromIterable(metrics.flatMap(encodeMetric(_, config, timestamp)))

  private def encodeAttributes(labels: Set[MetricLabel], additionalAttributes: Set[(String, Json)]): Json =
    Json.Obj(
      "attributes" -> Json.Obj(Chunk.fromIterable(labels.map { case MetricLabel(name, value) =>
        name -> Json.Str(value)
      } ++ additionalAttributes)),
    )

  private def encodeCommon(name: String, newRelicMetricType: String, timestamp: Long): Json.Obj =
    Json.Obj(
      "name"      -> Json.Str(name),
      "type"      -> Json.Str(newRelicMetricType),
      "timestamp" -> Json.Num(timestamp),
    )

  private def encodeCounter(
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

  private def encodeFrequency(
    occurrences: Map[String, Long],
    key: MetricKey.Untyped,
    interval: Long,
    timestamp: Long,
  ): Chunk[Json] =
    Chunk.fromIterable(occurrences.map { case (frequencyName, count) =>
      val tags: Set[(String, Json)] = Set(frequencyTagName -> Json.Str(frequencyName), makeZmxType("Frequency"))
      encodeCounter(count.toDouble, key, interval, timestamp, tags)
    })

  private def encodeGauge(
    value: Double,
    key: MetricKey.Untyped,
    timestamp: Long,
    additionalAttributes: Set[(String, Json)],
  ): Json =
    encodeCommon(key.name, "gauge", timestamp) merge Json.Obj(
      "value" -> Json.Num(value),
    ) merge encodeAttributes(key.tags, additionalAttributes)

  private def encodeHistogram(
    buckets: Chunk[(Double, Long)],
    count: Long,
    sum: Double,
    key: MetricKey.Untyped,
    interval: Long,
    timestamp: Long,
  ): Chunk[Json] = {

    val bucketValues = buckets.map(_._2.toDouble)
    val min          = bucketValues.minOption.getOrElse(0.0)
    val max          = bucketValues.maxOption.getOrElse(min)

    val zmxType = makeZmxType("Histogram")

    val encodedbuckets = buckets.map { case (bucket, value) =>
      val name          = s"${key.name}.bucket.$bucket"
      val addtionalTags =
        Set(
          s"zmx.histogram.id"     -> Json.Str(key.name), // Reference to the histogram metric to which this bucket belongs.
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

  private def encodeMetric(metric: MetricPair.Untyped, config: NewRelicConfig, timestamp: Long): Chunk[Json] =
    metric.metricState match {
      case Frequency(occurrences)                =>
        encodeFrequency(occurrences, metric.metricKey, config.defaultIntervalMillis, timestamp)
      case Summary(error, quantiles, count, sum) =>
        encodeSummary(error, quantiles, count, sum, metric.metricKey, config.defaultIntervalMillis, timestamp)
      case Counter(count)                        =>
        Chunk(
          encodeCounter(count, metric.metricKey, config.defaultIntervalMillis, timestamp, Set(makeZmxType("Counter"))),
        )
      case Histogram(buckets, count, sum)        =>
        encodeHistogram(buckets, count, sum, metric.metricKey, config.defaultIntervalMillis, timestamp)
      case Gauge(value)                          =>
        Chunk(encodeGauge(value, metric.metricKey, timestamp, Set(makeZmxType("Gauge"))))
    }

  private def encodeSummary(
    error: Double,
    quantiles: Chunk[(Double, Option[Double])],
    count: Long,
    sum: Double,
    key: MetricKey.Untyped,
    interval: Long,
    timestamp: Long,
  ): Chunk[Json] = {

    val quantileValues = quantiles.collect { case (_, Some(value)) =>
      value
    }

    val min = quantileValues.minOption.getOrElse(0.0)
    val max = quantileValues.maxOption.getOrElse(min)

    val zmxType = makeZmxType("Summary")

    val encodedQuantiles = quantiles.map { case (quantile, value) =>
      val name          = s"${key.name}.quantile.$quantile"
      val addtionalTags =
        Set(
          s"zmx.summary.id"       -> Json.Str(key.name), // Reference to the summary metric to which this quantile belongs.
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

  private val frequencyTagName = "zmx.frequency.key"

  private def makeNewRelicSummary(
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

  private def makeZmxType(zmxType: String): (String, Json) = "zmx.type" -> Json.Str(zmxType)
}
