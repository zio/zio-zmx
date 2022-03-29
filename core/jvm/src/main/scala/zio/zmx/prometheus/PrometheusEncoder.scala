package zio.zmx.prometheus

import java.time.Instant

import zio._
import zio.metrics._

private[prometheus] object PrometheusEncoder {

  def encode(
    metrics: Iterable[MetricPair.Untyped],
    timestamp: Instant,
  ): String =
    metrics.map(encodeMetric(_, timestamp)).mkString("\n")

  private def encodeMetric(
    metric: MetricPair.Untyped,
    timestamp: Instant,
  ): String = {

    def encodeCounter(c: MetricState.Counter, extraLabels: MetricLabel*): String =
      s"${encodeName(metric.metricKey.name)}${encodeLabels(extraLabels.toSet)} ${c.count} $encodeTimestamp"

    def encodeGauge(g: MetricState.Gauge): String =
      s"${encodeName(metric.metricKey.name)}${encodeLabels()} ${g.value} $encodeTimestamp"

    def encodeHistogram(h: MetricState.Histogram): String =
      encodeSamples(sampleHistogram(h), suffix = "_bucket").mkString("\n")

    def encodeSummary(s: MetricState.Summary): String = encodeSamples(sampleSummary(s), suffix = "").mkString("\n")

    // The header required for all Prometheus metrics
    def encodeHead: String =
      s"# TYPE ${encodeName(metric.metricKey.name)} $prometheusType\n" +
        s"# HELP ${encodeName(metric.metricKey.name)} Some help\n"

    def encodeName(s: String): String =
      s.replaceAll("-", "_")

    def encodeLabels(extraLabels: Set[MetricLabel] = Set.empty): String = {

      val allLabels = metric.metricKey.tags ++ extraLabels

      if (allLabels.isEmpty) ""
      else allLabels.map(l => l.key + "=\"" + l.value + "\"").mkString("{", ",", "} ")
    }

    def encodeSamples(samples: SampleResult, suffix: String): Chunk[String] =
      samples.buckets.map { b =>
        s"${encodeName(metric.metricKey.name)}$suffix${encodeLabels(b._1)} ${b._2.map(_.toString).getOrElse("NaN")} $encodeTimestamp"
          .trim()
      } ++ Chunk(
        s"${encodeName(metric.metricKey.name)}_sum${encodeLabels()} ${samples.sum} $encodeTimestamp".trim(),
        s"${encodeName(metric.metricKey.name)}_count${encodeLabels()} ${samples.count} $encodeTimestamp".trim(),
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
              if (s._1 == Double.MaxValue) Set(MetricLabel("le", "+Inf")) else Set(MetricLabel("le", s"${s._1}")),
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

    def prometheusType: String = metric.metricState match {
      case _: MetricState.Counter   => "counter"
      case _: MetricState.Gauge     => "gauge"
      case _: MetricState.Histogram => "histogram"
      case _: MetricState.Summary   => "summary"
      case _: MetricState.Frequency => "counter"
    }

    def encodeDetails = metric.metricState match {
      case c: MetricState.Counter   => encodeCounter(c)
      case g: MetricState.Gauge     => encodeGauge(g)
      case h: MetricState.Histogram => encodeHistogram(h)
      case s: MetricState.Summary   => encodeSummary(s)
      case s: MetricState.Frequency =>
        s.occurrences
          .map { o =>
            encodeCounter(MetricState.Counter(o._2.doubleValue()), MetricLabel("bucket", o._1))
          }
          .mkString("\n")
    }

    encodeHead ++ encodeDetails
  }

  private case class SampleResult(
    count: Double,
    sum: Double,
    buckets: Chunk[(Set[MetricLabel], Option[Double])])
}
