package zio.zmx.prometheus

import zio.Chunk

object PrometheusEncoder extends WithDoubleOrdering {

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
    metrics: List[Metric[_]],
    timestamp: java.time.Instant,
    duration: Option[java.time.Duration] = None
  ): String =
    metrics.map(m => encodeMetric(m, timestamp, duration).map(_.trim())).map(_.mkString("\n")).mkString("\n")

  private def encodeMetric(
    metric: Metric[_],
    timestamp: java.time.Instant,
    duration: Option[java.time.Duration]
  ): Seq[String] = {

    def encodeCounter(c: Metric.Counter): String =
      s"${metric.name}${encodeLabels()} ${c.count} ${encodeTimestamp}"

    def encodeGauge(g: Metric.Gauge): String =
      s"${metric.name}${encodeLabels()} ${g.value} ${encodeTimestamp}"

    def encodeHistogram(h: Metric.Histogram): Seq[String] = encodeSamples(sampleHistogram(h))

    def encodeSummary(s: Metric.Summary): Seq[String] = encodeSamples(sampleSummary(s))

    def encodeHead: Seq[String] =
      Seq(
        s"# TYPE ${metric.name} ${prometheusType}",
        s"# HELP ${metric.name} ${metric.help}"
      )

    def encodeLabels(extraLabels: Map[String, String] = Map.empty): String = {

      val allLabels = metric.labels ++ extraLabels

      if (allLabels.isEmpty) ""
      else allLabels.map { case (k, v) => k + "=\"" + v + "\"" }.mkString("{", ",", "}")
    }

    def encodeSamples(samples: SampleResult): Seq[String] =
      samples.buckets.map { b =>
        s"${metric.name}${encodeLabels(b._1)} ${b._2.map(_.toString).getOrElse("NaN")} ${encodeTimestamp}"
      }.toSeq ++ Seq(
        s"${metric.name}_sum${encodeLabels()} ${samples.sum} ${encodeTimestamp}",
        s"${metric.name}_count${encodeLabels()} ${samples.count} ${encodeTimestamp}"
      )

    def encodeTimestamp = s"${timestamp.toEpochMilli}"

    def sampleHistogram(h: Metric.Histogram): SampleResult = {
      val samples = h.buckets.map { case (k, ts) => (k, ts.timedSamples(timestamp, duration).length.toDouble) }
      SampleResult(
        count = h.count,
        sum = h.sum,
        buckets = samples.map { case (k, v) =>
          (if (k == Double.MaxValue) Map("le" -> "+Inf") else Map("le" -> s"$k"), Some(v))
        }
      )
    }

    def sampleSummary(s: Metric.Summary): SampleResult = {
      val qs = Quantile.calculateQuantiles(s.samples.timedSamples(timestamp, duration).map(_._1), s.quantiles)
      SampleResult(
        count = s.count,
        sum = s.sum,
        buckets = qs.map { case (k, v) => (Map("quantile" -> s"${k.phi}", "error" -> s"${k.error})"), v) }
      )
    }

    def prometheusType: String = metric.details match {
      case _: Metric.Counter   => "counter"
      case _: Metric.Gauge     => "gauge"
      case _: Metric.Histogram => "histogram"
      case _: Metric.Summary   => "summary"
    }

    encodeHead ++ (metric.details match {
      case c: Metric.Counter   => Seq(encodeCounter(c))
      case g: Metric.Gauge     => Seq(encodeGauge(g))
      case h: Metric.Histogram => encodeHistogram(h)
      case s: Metric.Summary   => encodeSummary(s)
    })
  }

  private case class SampleResult(
    count: Double,
    sum: Double,
    buckets: Chunk[(Map[String, String], Option[Double])]
  )

}
