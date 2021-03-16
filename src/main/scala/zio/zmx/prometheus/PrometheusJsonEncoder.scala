package zio.zmx.prometheus

import zio.zmx.Label
import zio.zmx.prometheus.PMetric.{ Counter, Details, Gauge, Histogram, Summary }

import java.time.Instant

object PrometheusJsonEncoder {

  def jsonString(s: String): String =
    "\"" + s.replaceAll("\"", "\\\"") + "\""

  def jsonArray[A](as: Iterable[A], f: A => String) =
    as.map(f).mkString("[", ",", "]")

  def jsonObject(fields: Label*) =
    fields.map(field => s"${jsonString(field._1)}:${field._2}").mkString("{", ",", "}")

  def encodePMetric(m: PMetric): String =
    jsonObject(
      "name"    -> jsonString(m.name),
      "help"    -> jsonString(m.help),
      "labels"  -> jsonArray(m.labels, (l: Label) => s"[${jsonString(l._1)},${jsonString(l._2)}]"),
      "details" -> encodeDetails(m.details)
    )

  def encodeDetails(d: Details): String = d match {
    case c: Counter   => encodeCounter(c)
    case c: Gauge     => encodeGauge(c)
    case c: Histogram => encodeHistogram(c)
    case c: Summary   => encodeSummary(c)
  }

  def encodeCounter(a: Counter)     =
    jsonObject("Counter" -> jsonObject("count" -> a.count.toString))

  def encodeGauge(a: Gauge)         =
    jsonObject("Gauge" -> jsonObject("value" -> a.value.toString))

  def encodeHistogram(a: Histogram) =
    jsonObject(
      "Histogram" -> jsonObject(
        "buckets" -> jsonArray(a.buckets, (b: (Double, Double)) => s"[${b._1.toString},${b._2.toString}]"),
        "count"   -> a.count.toString,
        "sum"     -> a.sum.toString
      )
    )

  def encodeInstant(a: Instant) =
    jsonString(a.toString)

  def encodeSample(a: (Double, Instant)) =
    s"""[${a._1.toString},${encodeInstant(a._2)}]"""

  def encodeTimeSeries(a: TimeSeries) =
    jsonObject(
      "maxAge"  -> jsonString(a.maxAge.toString),
      "maxSize" -> a.maxSize.toString,
      "samples" -> jsonArray(a.samples, encodeSample)
    )

  def encodeQuantile(a: Quantile) =
    jsonObject(
      "phi"   -> a.phi.toString,
      "error" -> a.error.toString
    )

  def encodeSummary(a: Summary) =
    jsonObject(
      "Summary" -> jsonObject(
        "samples"   -> encodeTimeSeries(a.samples),
        "quantiles" -> jsonArray(a.quantiles, encodeQuantile),
        "count"     -> a.count.toString,
        "sum"       -> a.sum.toString
      )
    )
}
