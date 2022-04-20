package zio.zmx.statsd

import java.text.DecimalFormat
import java.time.Instant

import zio._
import zio.metrics._

private[statsd] object StatsdEncoder {

  private val BUF_PER_METRIC = 30

  def encode(
    metrics: Iterable[MetricPair.Untyped],
    timestamp: Instant,
  ): String = {

    val result = new StringBuilder(metrics.size * BUF_PER_METRIC)

    metrics.foreach { mp =>
      mp.metricState match {
        case c: MetricState.Counter   => appendCounter(result, mp.metricKey, c)
        case g: MetricState.Gauge     => appendGauge(result, mp.metricKey, g)
        case h: MetricState.Histogram => appendHistogram(result, mp.metricKey, h)
        case s: MetricState.Summary   => appendSummary(result, mp.metricKey, s)
        case f: MetricState.Frequency => appendFrequency(result, mp.metricKey, f)
      }
    }

    result.toString()
  }

  // TODO: We need to determine the delta for the counter since we have last reported it
  // Perhaps we can see the rate for gauges in the backend, so we could report just theses
  // For a counter we only report the last observed value to statsd
  private def appendCounter(buf: StringBuilder, key: MetricKey.Untyped, c: MetricState.Counter): Unit =
    appendMetric(buf, key.name, c.count, "c", key.tags)

  // For a gauge we report the current value to statsd
  private def appendGauge(buf: StringBuilder, key: MetricKey.Untyped, g: MetricState.Gauge): Unit =
    appendMetric(buf, key.name, g.value, "g", key.tags)

  // A Histogram is reported to statsd as a set of related gauges, distinguished by an additional label
  private def appendHistogram(buf: StringBuilder, key: MetricKey.Untyped, h: MetricState.Histogram): Unit = {
    h.buckets
      .foreach { case (boundary, count) =>
        val bucket = if (boundary < Double.MaxValue) boundary.toString() else "Inf"
        if (buf.nonEmpty) buf.append("\n")
        appendMetric(buf, key.name, count.doubleValue, "g", key.tags, MetricLabel("le", bucket))
      }

    buf.toString()
  }

  // A Summary is reported to statsd as a set of related gauges, distinguished by an additional label
  // for the quantile and another label for the error margin
  private def appendSummary(buf: StringBuilder, key: MetricKey.Untyped, s: MetricState.Summary): Unit = {
    s.quantiles
      .foreach { case (q, v) =>
        v.foreach { v =>
          appendMetric(
            buf,
            key.name,
            v,
            "g",
            key.tags,
            MetricLabel("quantile", q.toString()),
            MetricLabel("error", s.error.toString()),
          )
        }
      }

    buf.toString()
  }

  // For each individual observed String we are going to report a counter to statsd with an
  // additional label with key "bucket" and the observed String as a value
  private def appendFrequency(buf: StringBuilder, key: MetricKey.Untyped, f: MetricState.Frequency): Unit =
    f.occurrences.foreach { case (b, c) =>
      appendMetric(
        buf,
        key.name,
        c.doubleValue(),
        "g",
        key.tags,
        MetricLabel("bucket", b),
      )
    }

  private def appendMetric(
    buf: StringBuilder,
    name: String,
    value: Double,
    metricType: String,
    tags: Set[MetricLabel],
    extraTags: MetricLabel*,
  ): Unit = {
    val tagBuf = new StringBuilder()
    appendTags(tagBuf, tags)
    appendTags(tagBuf, extraTags)

    buf.append(name)
    buf.append(":")
    buf.append(format.format(value))
    buf.append("|")
    buf.append(metricType)

    if (tagBuf.nonEmpty) {
      buf.append("|#")
      buf.append(tagBuf)
    }
  }

  private def appendTag(buf: StringBuilder, tag: MetricLabel): Unit = {
    if (buf.nonEmpty) buf.append(",")
    buf.append(tag.key)
    buf.append(":")
    buf.append(tag.value)
  }

  private def appendTags(buf: StringBuilder, tags: Iterable[MetricLabel]): Unit =
    tags.foreach(appendTag(buf, _))

  private lazy val format = new DecimalFormat("0.################")

}
