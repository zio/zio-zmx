package zio.zmx.prometheus

import com.github.ghik.silencer.silent

import zio.Chunk
import zio.zmx.Label

object PrometheusEncoder {

  /**
   * Encode a given List of Metrics according to the Prometheus client specification. The specification is
   * at https://prometheus.io/docs/instrumenting/exposition_formats/#text-based-format. For our purposes
   * the encoding always requires the instant at which the encoding occurs. For histograms and summaries
   * an optional duration can be specified.
   *
   * If a duration is provided, the encoding for histograms and summaries takes only the samples for
   * <code>timestamp - duration</code> into account. If the duration is omitted, the encodin g will use the
   * maxAge duration for each individual histogram or summary.
   */
  def encode(
    metrics: List[PMetric],
    timestamp: java.time.Instant,
    duration: Option[java.time.Duration] = None
  ): String =
    metrics.map(m => encodeMetric(m, timestamp, duration).map(_.trim())).map(_.mkString("\n")).mkString("\n")

  private def encodeMetric(
    metric: PMetric,
    timestamp: java.time.Instant,
    duration: Option[java.time.Duration]
  ): Seq[String] = {

    def encodeCounter(c: PMetric.Counter): String =
      s"${metric.name}${encodeLabels()} ${c.count} ${encodeTimestamp}"

    def encodeGauge(g: PMetric.Gauge): String =
      s"${metric.name}${encodeLabels()} ${g.value} ${encodeTimestamp}"

    def encodeHistogram(h: PMetric.Histogram): Seq[String] = encodeSamples(sampleHistogram(h))

    def encodeSummary(s: PMetric.Summary): Seq[String] = encodeSamples(sampleSummary(s))

    def encodeHead: Seq[String] =
      Seq(
        s"# TYPE ${metric.name} ${prometheusType}",
        s"# HELP ${metric.name} ${metric.help}"
      )

    def encodeLabels(extraLabels: Chunk[Label] = Chunk.empty): String = {

      val allLabels = metric.labels ++ extraLabels

      if (allLabels.isEmpty) ""
      else allLabels.map(l => l._1 + "=\"" + l._2 + "\"").mkString("{", ",", "}")
    }

    def encodeSamples(samples: SampleResult): Seq[String] =
      samples.buckets.map { b =>
        s"${metric.name}${encodeLabels(b._1)} ${b._2.map(_.toString).getOrElse("NaN")} ${encodeTimestamp}"
      }.toSeq ++ Seq(
        s"${metric.name}_sum${encodeLabels()} ${samples.sum} ${encodeTimestamp}",
        s"${metric.name}_count${encodeLabels()} ${samples.count} ${encodeTimestamp}"
      )

    def encodeTimestamp = s"${timestamp.toEpochMilli}"

    def sampleHistogram(h: PMetric.Histogram): SampleResult =
      SampleResult(
        count = h.count,
        sum = h.sum,
        buckets = h.buckets.map { s =>
          (if (s._1 == Double.MaxValue) Chunk("le" -> "+Inf") else Chunk("le" -> s"${s._1}"), Some(s._2))
        }
      )

    def sampleSummary(s: PMetric.Summary): SampleResult = {
      val qs = Quantile.calculateQuantiles(s.samples.timedSamples(timestamp, duration).map(_._1), s.quantiles)
      SampleResult(
        count = s.count,
        sum = s.sum,
        buckets = qs.map(q => Chunk("quantile" -> q._1.phi.toString, "error" -> q._1.error.toString) -> q._2)
      )
    }

    @silent
    def prometheusType: String = metric.details match {
      case _: PMetric.Counter   => "counter"
      case _: PMetric.Gauge     => "gauge"
      case _: PMetric.Histogram => "histogram"
      case _: PMetric.Summary   => "summary"
    }

    @silent
    def encodeDetails = metric.details match {
      case c: PMetric.Counter   => Seq(encodeCounter(c))
      case g: PMetric.Gauge     => Seq(encodeGauge(g))
      case h: PMetric.Histogram => encodeHistogram(h)
      case s: PMetric.Summary   => encodeSummary(s)
    }

    encodeHead ++ encodeDetails
  }

  private case class SampleResult(
    count: Double,
    sum: Double,
    buckets: Chunk[(Chunk[Label], Option[Double])]
  )

}
