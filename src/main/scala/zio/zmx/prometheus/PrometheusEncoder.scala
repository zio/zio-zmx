package zio.zmx.prometheus

object PrometheusEncoder {

  def encode(metrics: List[Metric]): String = metrics.map(encode).mkString("\n")

  private def encode(metric: Metric): String = encodeHead(metric) + (metric.metricType match {
    case c: MetricType.Counter   => encodeCounter(c)
    case g: MetricType.Gauge     => encodeGauge(g)
    case h: MetricType.Histogram => encodeHistogram(h)
    case s: MetricType.Summary   => encodeSummary(s)
  })

  private def encodeHead(metric: Metric): String = ???

  private def encodeCounter(c: MetricType.Counter): String     = ???
  private def encodeGauge(g: MetricType.Gauge): String         = ???
  private def encodeHistogram(h: MetricType.Histogram): String = ???
  private def encodeSummary(s: MetricType.Summary): String     = ???
}
