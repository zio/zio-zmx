package zio.zmx.prometheus

object PrometheusEncoder {

  def encode(metrics: List[Metric], timestamp: Option[java.time.Instant]): String = metrics.map(m => encode(m, timestamp)).mkString("\n")

  private def encode(metric: Metric, timestamp : Option[java.time.Instant]): String = {

    def encodeCounter(suffix: Option[String], extraLabel : Option[(String, String)], c: MetricType.Counter): String =
      s"${metric.name}${suffix.getOrElse("")} ${encodeLabels(extraLabel)} ${c.count} ${encodeTimestamp}"

    def encodeGauge(g: MetricType.Gauge): String =
      s"${metric.name} ${encodeLabels(None)} ${g.value} ${encodeTimestamp}"

    def encodeHistogram(h: MetricType.Histogram): String = {

      val buckets = h.buckets.map{ case (k, c) => encodeCounter(Some("_bucket"), Some(("le", s"$k")), c) }

      buckets.mkString("\n") +
      s"${metric.name}_sum ${encodeLabels(None)} ${h.sum} ${encodeTimestamp}\n" +
      s"${metric.name}_count ${encodeLabels(None)} ${h.cnt} ${encodeTimestamp}\n"
    }

    def encodeSummary(s: MetricType.Summary): String = ???

    def encodeHead: String = {
      val headLines: List[String] = List(
        s"# TYPE ${metric.name} ${prometheusType(metric.metricType)}"
      ) ++ metric.help.map(h => s"# HELP ${metric.name} $h").toList

      headLines.mkString("", "\n", "\n")
    }

    def encodeTimestamp = timestamp.map(ts => s"${ts.toEpochMilli()}").getOrElse("")

    def encodeLabels(extraLabel: Option[(String, String)]): String = {

      val allLabels = metric.labels ++ extraLabel.toMap

      if (allLabels.isEmpty) ""
      else allLabels.map { case (k, v) => k + "\"" + v + "\"" }.mkString("{", ",", "}")
    }

    def prometheusType(mt: MetricType): String = mt match {
      case _: MetricType.Counter   => "counter"
      case _: MetricType.Gauge     => "gauge"
      case _: MetricType.Histogram => "histogram"
      case _: MetricType.Summary   => "summary"
    }

    encodeHead + (metric.metricType match {
      case c: MetricType.Counter   => encodeCounter(None, None, c)
      case g: MetricType.Gauge     => encodeGauge(g)
      case h: MetricType.Histogram => encodeHistogram(h)
      case s: MetricType.Summary   => encodeSummary(s)
    })
  }
}
