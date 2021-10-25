package zio.zmx.prometheus

import java.time.Instant

import zio._
import zio.metrics._

private[prometheus] object PrometheusEncoder {

  def encode(
    metrics: Iterable[MetricState],
    timestamp: Instant
  ): String =
    metrics.map(encodeMetric(_, timestamp)).mkString("\n")

  private def encodeMetric(
    metric: MetricState,
    timestamp: Instant
  ): String = {

    def encodeCounter(c: MetricType.Counter, extraLabels: MetricLabel*): String =
      s"${metric.name}${encodeLabels(Chunk.fromIterator(extraLabels.iterator))} ${c.count} ${encodeTimestamp}"

    def encodeGauge(g: MetricType.Gauge): String =
      s"${metric.name}${encodeLabels()} ${g.value} ${encodeTimestamp}"

    def encodeHistogram(h: MetricType.DoubleHistogram): String =
      encodeSamples(sampleHistogram(h), suffix = "_bucket").mkString("\n")

    def encodeSummary(s: MetricType.Summary): String = encodeSamples(sampleSummary(s), suffix = "").mkString("\n")

    // The header required for all Prometheus metrics
    def encodeHead: String =
      s"# TYPE ${metric.name} ${prometheusType}\n" +
        s"# HELP ${metric.name} ${metric.help}\n"

    def encodeLabels(extraLabels: Chunk[MetricLabel] = Chunk.empty): String = {

      val allLabels = metric.labels ++ extraLabels

      if (allLabels.isEmpty) ""
      else allLabels.map(l => l.key + "=\"" + l.value + "\"").mkString("{", ",", "}")
    }

    def encodeSamples(samples: SampleResult, suffix: String): Chunk[String] =
      samples.buckets.map { b =>
        s"${metric.name}$suffix${encodeLabels(b._1)} ${b._2.map(_.toString).getOrElse("NaN")} ${encodeTimestamp}".trim()
      } ++ Chunk(
        s"${metric.name}_sum${encodeLabels()} ${samples.sum} ${encodeTimestamp}".trim(),
        s"${metric.name}_count${encodeLabels()} ${samples.count} ${encodeTimestamp}".trim()
      )

    def encodeTimestamp = s"${timestamp.toEpochMilli()}"

    def sampleHistogram(h: MetricType.DoubleHistogram): SampleResult =
      SampleResult(
        count = h.count.doubleValue(),
        sum = h.sum,
        buckets = h.buckets
          .filter(_._1 != Double.MaxValue)
          .sortBy(_._1)
          .map { s =>
            (
              if (s._1 == Double.MaxValue) Chunk(MetricLabel("le", "+Inf")) else Chunk(MetricLabel("le", s"${s._1}")),
              Some(s._2.doubleValue())
            )
          } :+ ((Chunk(MetricLabel("le", "+Inf")) -> Some(h.count.doubleValue())))
      )

    def sampleSummary(s: MetricType.Summary): SampleResult =
      SampleResult(
        count = s.count.doubleValue(),
        sum = s.sum,
        buckets = s.quantiles.map(q =>
          Chunk(MetricLabel("quantile", q._1.toString), MetricLabel("error", s.error.toString)) -> q._2
        )
      )

    def prometheusType: String = metric.details match {
      case _: MetricType.Counter         => "counter"
      case _: MetricType.Gauge           => "gauge"
      case _: MetricType.DoubleHistogram => "histogram"
      case _: MetricType.Summary         => "summary"
      case _: MetricType.SetCount        => "counter"
    }

    def encodeDetails = metric.details match {
      case c: MetricType.Counter         => encodeCounter(c)
      case g: MetricType.Gauge           => encodeGauge(g)
      case h: MetricType.DoubleHistogram => encodeHistogram(h)
      case s: MetricType.Summary         => encodeSummary(s)
      case s: MetricType.SetCount        =>
        s.occurrences.map { o =>
          encodeCounter(MetricType.Counter(o._2.doubleValue()), MetricLabel(s.setTag, o._1))
        }.mkString("\n")
    }

    encodeHead ++ encodeDetails
  }

  private case class SampleResult(
    count: Double,
    sum: Double,
    buckets: Chunk[(Chunk[MetricLabel], Option[Double])]
  )
}
