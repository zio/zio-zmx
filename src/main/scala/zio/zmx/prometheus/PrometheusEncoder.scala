package zio.zmx.prometheus

object PrometheusEncoder {

  def encode(metrics: List[Metric]): String = metrics.map(encode).mkString("\n")

  private def encode(metric: Metric): String = {

    /*
     * metric_name [
     * "{" label_name "=" `"` label_value `"` { "," label_name "=" `"` label_value `"` } [ "," ] "}"
     * ] value [ timestamp ]
     */

    def encodeCounter(c: MetricType.Counter): String     =
      s"${encodeName} + ${c.count} + ${java.time.Instant.now.toEpochMilli}"
    def encodeGauge(g: MetricType.Gauge): String         =
      s"${encodeName} + ${g.value} + ${java.time.Instant.now.toEpochMilli}"
    def encodeHistogram(h: MetricType.Histogram): String = ???
    def encodeSummary(s: MetricType.Summary): String     = ???

    def encodeHead: String = {
      val headLines: List[String] = List(
        s"# TYPE ${metric.name} ${prometheusType(metric.metricType)}"
      ) ++ metric.help.map(h => s"# HELP $h").toList

      headLines.mkString("", "\n", "\n")
    }

    def encodeName(): String = ???

    def prometheusType(mt: MetricType): String = mt match {
      case _: MetricType.Counter   => "counter"
      case _: MetricType.Gauge     => "gauge"
      case _: MetricType.Histogram => "histogram"
      case _: MetricType.Summary   => "summary"
    }

    encodeHead + (metric.metricType match {
      case c: MetricType.Counter   => encodeCounter(c)
      case g: MetricType.Gauge     => encodeGauge(g)
      case h: MetricType.Histogram => encodeHistogram(h)
      case s: MetricType.Summary   => encodeSummary(s)
    })
  }
}
