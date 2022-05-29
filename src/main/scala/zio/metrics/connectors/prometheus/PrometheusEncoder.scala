package zio.metrics.connectors.prometheus

import java.time.Instant

import zio._
import zio.metrics._
import zio.metrics.connectors._

final case object PrometheusEncoder {

  def encode(event: MetricEvent): ZIO[Any, Throwable, Chunk[String]] =
    ZIO.attempt(encodeMetric(event.metricKey, event.current, event.timestamp))

  private def encodeMetric(
    key: MetricKey.Untyped,
    state: MetricState.Untyped,
    timestamp: Instant,
  ): Chunk[String] = {

    def encodeCounter(c: MetricState.Counter, extraLabels: MetricLabel*): String =
      s"${encodeName(key.name)}${encodeLabels(extraLabels.toSet)} ${c.count} $encodeTimestamp"

    def encodeGauge(g: MetricState.Gauge): String =
      s"${encodeName(key.name)}${encodeLabels()} ${g.value} $encodeTimestamp"

    def encodeHistogram(h: MetricState.Histogram): Chunk[String] =
      encodeSamples(sampleHistogram(h), suffix = "_bucket")

    def encodeSummary(s: MetricState.Summary): Chunk[String] =
      encodeSamples(sampleSummary(s), suffix = "")

    // The header required for all Prometheus metrics
    def encodeHead: Chunk[String] = Chunk(
      s"# TYPE ${encodeName(key.name)} $prometheusType",
      s"# HELP ${encodeName(key.name)} Some help",
    )

    def encodeName(s: String): String =
      s.replaceAll("-", "_")

    def encodeLabels(extraLabels: Set[MetricLabel] = Set.empty): String = {

      val allLabels = key.tags ++ extraLabels

      if (allLabels.isEmpty) ""
      else allLabels.map(l => l.key + "=\"" + l.value + "\"").mkString("{", ",", "} ")
    }

    def encodeSamples(samples: SampleResult, suffix: String): Chunk[String] =
      samples.buckets.map { b =>
        s"${encodeName(key.name)}$suffix${encodeLabels(b._1)} ${b._2.map(_.toString).getOrElse("NaN")} $encodeTimestamp"
          .trim()
      } ++ Chunk(
        s"${encodeName(key.name)}_sum${encodeLabels()} ${samples.sum} $encodeTimestamp".trim(),
        s"${encodeName(key.name)}_count${encodeLabels()} ${samples.count} $encodeTimestamp".trim(),
      )

    def encodeTimestamp = s"${timestamp.toEpochMilli()}"

    def sampleHistogram(h: MetricState.Histogram): SampleResult =
      SampleResult(
        count = h.count.doubleValue(),
        sum = h.sum,
        buckets = h.buckets
          .filter(_._1 != Double.MaxValue)
          .sortBy(_._1)
          .map { s =>
            (
              Set(MetricLabel("le", s"${s._1}")),
              Some(s._2.doubleValue()),
            )
          } :+ (Set(MetricLabel("le", "+Inf")) -> Some(h.count.doubleValue())),
      )

    def sampleSummary(s: MetricState.Summary): SampleResult =
      SampleResult(
        count = s.count.doubleValue(),
        sum = s.sum,
        buckets = s.quantiles.map(q =>
          Set(MetricLabel("quantile", q._1.toString), MetricLabel("error", s.error.toString)) -> q._2,
        ),
      )

    def prometheusType: String = state match {
      case _: MetricState.Counter   => "counter"
      case _: MetricState.Gauge     => "gauge"
      case _: MetricState.Histogram => "histogram"
      case _: MetricState.Summary   => "summary"
      case _: MetricState.Frequency => "counter"
    }

    def encodeDetails: Chunk[String] = state match {
      case c: MetricState.Counter   => Chunk(encodeCounter(c))
      case g: MetricState.Gauge     => Chunk(encodeGauge(g))
      case h: MetricState.Histogram => encodeHistogram(h)
      case s: MetricState.Summary   => encodeSummary(s)
      case s: MetricState.Frequency =>
        Chunk.fromIterable(
          s.occurrences
            .map { o =>
              encodeCounter(MetricState.Counter(o._2.doubleValue()), MetricLabel("bucket", o._1))
            },
        )
    }

    encodeHead ++ encodeDetails
  }

  private case class SampleResult(
    count: Double,
    sum: Double,
    buckets: Chunk[(Set[MetricLabel], Option[Double])])
}

