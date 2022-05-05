package zio.metrics.connectors.statsd

import java.text.DecimalFormat

import zio._
import zio.metrics._
import zio.metrics.connectors._

final case object StatsdEncoder extends MetricEncoder[Byte] {

  private val BUF_PER_METRIC = 128

  override def encode(event: MetricEvent): Task[Chunk[Byte]] =
    ZIO.attempt(encodeEvent(event))

  def encodeEvent(
    event: MetricEvent,
  ): Chunk[Byte] = {

    val result = new StringBuilder(BUF_PER_METRIC)

    event.current match {
      case c: MetricState.Counter   => appendCounter(result, event.metricKey, c)
      case g: MetricState.Gauge     => appendGauge(result, event.metricKey, g)
      case h: MetricState.Histogram => appendHistogram(result, event.metricKey, h)
      case s: MetricState.Summary   => appendSummary(result, event.metricKey, s)
      case f: MetricState.Frequency => appendFrequency(result, event.metricKey, f)
    }

    Chunk.fromArray(result.toString().getBytes())
  }

  // TODO: We need to determine the delta for the counter since we have last reported it
  // Perhaps we can see the rate for gauges in the backend, so we could report just theses
  // For a counter we only report the last observed value to statsd
  private def appendCounter(buf: StringBuilder, key: MetricKey.Untyped, c: MetricState.Counter): StringBuilder =
    appendMetric(buf, key.name, c.count, "c", key.tags)

  // For a gauge we report the current value to statsd
  private def appendGauge(buf: StringBuilder, key: MetricKey.Untyped, g: MetricState.Gauge): StringBuilder =
    appendMetric(buf, key.name, g.value, "g", key.tags)

  // A Histogram is reported to statsd as a set of related gauges, distinguished by an additional label
  private def appendHistogram(buf: StringBuilder, key: MetricKey.Untyped, h: MetricState.Histogram): StringBuilder =
    h.buckets.foldLeft(buf) { case (cur, (boundary, count)) =>
      val bucket = if (boundary < Double.MaxValue) boundary.toString() else "Inf"
      appendMetric(cur, key.name, count.doubleValue(), "g", key.tags, MetricLabel("le", bucket))
    }

  // A Summary is reported to statsd as a set of related gauges, distinguished by an additional label
  // for the quantile and another label for the error margin
  private def appendSummary(buf: StringBuilder, key: MetricKey.Untyped, s: MetricState.Summary): StringBuilder =
    s.quantiles.foldLeft(buf) { case (cur, (q, v)) =>
      v match {
        case None    => cur
        case Some(v) =>
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

  // For each individual observed String we are going to report a counter to statsd with an
  // additional label with key "bucket" and the observed String as a value
  private def appendFrequency(buf: StringBuilder, key: MetricKey.Untyped, f: MetricState.Frequency): StringBuilder =
    f.occurrences.foldLeft(buf) { case (cur, (b, c)) =>
      appendMetric(
        cur,
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
  ): StringBuilder = {
    val tagBuf      = new StringBuilder()
    val withTags    = appendTags(tagBuf, tags)
    val withAllTags = appendTags(withTags, extraTags)

    val withLF = if (buf.nonEmpty) buf.append("\n") else buf

    val withMetric = withLF
      .append(name)
      .append(":")
      .append(format.format(value))
      .append("|")
      .append(metricType)

    if (withAllTags.nonEmpty) {
      withMetric.append("|#").append(tagBuf)
    } else withMetric
  }

  private def appendTag(buf: StringBuilder, tag: MetricLabel): StringBuilder = {
    if (buf.nonEmpty) buf.append(",")
    buf.append(tag.key).append(":").append(tag.value)
  }

  private def appendTags(buf: StringBuilder, tags: Iterable[MetricLabel]): StringBuilder =
    tags.foldLeft(buf) { case (cur, tag) => appendTag(cur, tag) }

  private lazy val format = new DecimalFormat("0.################")

}
