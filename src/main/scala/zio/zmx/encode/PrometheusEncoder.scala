package zio.zmx.prometheus

import com.github.ghik.silencer.silent

import java.time.Instant

import zio.Chunk
import zio.zmx.Label
import zio.zmx.state._

object PrometheusEncoder {

  def encode(
    metrics: Chunk[MetricState],
    timestamp: Instant
  ): String =
    metrics.map(encodeMetric(_, timestamp)).mkString("\n")

  private def encodeMetric(
    metric: MetricState,
    timestamp: Instant
  ): String = {

    def encodeCounter(c: MetricType.Counter): String =
      s"${metric.name}${encodeLabels()} ${c.count} ${encodeTimestamp}"

    def encodeGauge(g: MetricType.Gauge): String =
      s"${metric.name}${encodeLabels()} ${g.value} ${encodeTimestamp}"

    def encodeHistogram(h: MetricType.DoubleHistogram): String = encodeSamples(sampleHistogram(h)).mkString("\n")

    def encodeSummary(s: MetricType.Summary): String = encodeSamples(sampleSummary(s)).mkString("\n")

    // The header required for all Prometheus metrics
    def encodeHead: String =
      s"# TYPE ${metric.name} ${prometheusType}\n" +
        s"# HELP ${metric.name} ${metric.help}\n"

    def encodeLabels(extraLabels: Chunk[Label] = Chunk.empty): String = {

      val allLabels = metric.labels ++ extraLabels

      if (allLabels.isEmpty) ""
      else allLabels.map(l => l._1 + "=\"" + l._2 + "\"").mkString("{", ",", "}")
    }

    def encodeSamples(samples: SampleResult): Chunk[String] =
      samples.buckets.map { b =>
        s"${metric.name}${encodeLabels(b._1)} ${b._2.map(_.toString).getOrElse("NaN")} ${encodeTimestamp}".trim()
      } ++ Chunk(
        s"${metric.name}_sum${encodeLabels()} ${samples.sum} ${encodeTimestamp}".trim(),
        s"${metric.name}_count${encodeLabels()} ${samples.count} ${encodeTimestamp}.trim()"
      )

    def encodeTimestamp = s"${timestamp.toEpochMilli}"

    def sampleHistogram(h: MetricType.DoubleHistogram): SampleResult =
      SampleResult(
        count = h.count,
        sum = h.sum,
        buckets = h.buckets.map { s =>
          (if (s._1 == Double.MaxValue) Chunk("le" -> "+Inf") else Chunk("le" -> s"${s._1}"), Some(s._2))
        }
      )

    def sampleSummary(s: MetricType.Summary): SampleResult =
      SampleResult(
        count = s.count,
        sum = s.sum,
        buckets = s.quantiles.map(q => Chunk("quantile" -> q.toString, "error" -> s.error.toString) -> q._2)
      )

    def prometheusType: String = metric.details match {
      case _: MetricType.Counter         => "counter"
      case _: MetricType.Gauge           => "gauge"
      case _: MetricType.DoubleHistogram => "histogram"
      case _: MetricType.Summary         => "summary"
    }

    @silent
    def encodeDetails = metric.details match {
      case c: MetricType.Counter         => encodeCounter(c)
      case g: MetricType.Gauge           => encodeGauge(g)
      case h: MetricType.DoubleHistogram => encodeHistogram(h)
      case s: MetricType.Summary         => encodeSummary(s)
    }

    encodeHead ++ encodeDetails
  }

  private case class SampleResult(
    count: Double,
    sum: Double,
    buckets: Chunk[(Chunk[Label], Option[Double])]
  )
}
