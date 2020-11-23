package zio.zmx.prometheus

object PrometheusEncoder {

  def encode(metrics: List[Metric], timestamp: Option[java.time.Instant]): String =
    metrics.map(m => encode(m, timestamp).map(_.trim())).map(_.mkString("\n")).mkString("\n")

  private def encode(metric: Metric, timestamp: Option[java.time.Instant]): Seq[String] = {

    def encodeCounter(suffix: Option[String], extraLabel: Option[(String, String)], c: Metric.Counter): String =
      s"${metric.name}${suffix.getOrElse("")} ${encodeLabels(extraLabel)} ${c.count} ${encodeTimestamp}"

    def encodeGauge(g: Metric.Gauge): String =
      s"${metric.name} ${encodeLabels(None)} ${g.value} ${encodeTimestamp}"

    def encodeHistogram(h: Metric.Histogram): Seq[String] = {

      val buckets = h.buckets.map { case (k, c) => encodeCounter(Some("_bucket"), Some(("le", s"$k")), c) }

      buckets.toSeq ++ Seq(
        s"${metric.name}_sum ${encodeLabels(None)} ${h.sum} ${encodeTimestamp}",
        s"${metric.name}_count ${encodeLabels(None)} ${h.cnt} ${encodeTimestamp}"
      )
    }

    // TODO: need to figure out implementation for Quantiles and finish encoding
    def encodeSummary(s: Metric.Summary): Seq[String] = {

      val quantiles = Seq.empty[String] // quantiles.map { case Metric.Quantile(_, _) => ??? }

      quantiles ++ Seq(
        s"${metric.name}_sum ${encodeLabels(None)} ${s.sum} ${encodeTimestamp}",
        s"${metric.name}_count ${encodeLabels(None)} ${s.cnt} ${encodeTimestamp}"
      )
    }

    def encodeHead: Seq[String] =
      Seq(
        s"# TYPE ${metric.name} ${prometheusType(metric)}"
      ) ++ metric.help.map(h => s"# HELP ${metric.name} $h").toSeq

    def encodeTimestamp = timestamp.map(ts => s"${ts.toEpochMilli()}").getOrElse("")

    def encodeLabels(extraLabel: Option[(String, String)]): String = {

      val allLabels = metric.labels ++ extraLabel.toMap

      if (allLabels.isEmpty) ""
      else allLabels.map { case (k, v) => k + "=\"" + v + "\"" }.mkString("{", ",", "}")
    }

    def prometheusType(mt: Metric): String = mt match {
      case _: Metric.Counter   => "counter"
      case _: Metric.Gauge     => "gauge"
      case _: Metric.Histogram => "histogram"
      case _: Metric.Summary   => "summary"
    }

    encodeHead ++ (metric match {
      case c: Metric.Counter   => Seq(encodeCounter(None, None, c))
      case g: Metric.Gauge     => Seq(encodeGauge(g))
      case h: Metric.Histogram => encodeHistogram(h)
      case s: Metric.Summary   => encodeSummary(s)
    })
  }
}
