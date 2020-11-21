package zio.zmx.prometheus

object PrometheusEncoder {

  def encode(metrics: List[Metric]): String = metrics.map(encode).mkString("\n")

  private def encode(metric: Metric): String = {

    def encodeCounter(c: MetricType.Counter): String     = ???
    def encodeGauge(g: MetricType.Gauge): String         = ???
    def encodeHistogram(h: MetricType.Histogram): String = ???
    def encodeSummary(s: MetricType.Summary): String     = ???

    def encodeHead : String = {
      val headLines: List[String] = List(
        s"# TYPE ${metric.name} ${prometheusType(metric.metricType)}"
      ) ++ metric.help.map(h => s"# HELP $h").toList

      headLines.mkString("", "\n", "\n")
    }

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
