package zio.zmx.prometheus

import zio.Chunk
import zio.zmx.Label
import zio.zmx.state._

import java.time.Instant

object JsonEncoder {

  def encode(
    metrics: Chunk[MetricState]
  ): String = jsonArray(metrics, encodeMetricState)

  def jsonString(s: String): String =
    "\"" + s.replaceAll("\"", "\\\"") + "\""

  def jsonArray[A](as: Iterable[A], f: A => String) =
    as.map(f).mkString("[", ",", "]")

  def jsonObject(fields: Label*) =
    fields.map(field => s"${jsonString(field._1)}:${field._2}").mkString("{", ",", "}")

  def encodeMetricState(m: MetricState): String =
    jsonObject(
      "name"    -> jsonString(m.name),
      "help"    -> jsonString(m.help),
      "labels"  -> jsonArray(m.labels, (l: Label) => s"[${jsonString(l._1)},${jsonString(l._2)}]"),
      "details" -> encodeDetails(m.details)
    )

  def encodeDetails(d: MetricType): String = d match {
    case c: MetricType.Counter         => encodeCounter(c)
    case c: MetricType.Gauge           => encodeGauge(c)
    case c: MetricType.DoubleHistogram => encodeHistogram(c)
    case c: MetricType.Summary         => encodeSummary(c)
  }

  def encodeCounter(a: MetricType.Counter)           =
    jsonObject("Counter" -> jsonObject("count" -> a.count.toString))

  def encodeGauge(a: MetricType.Gauge)               =
    jsonObject("Gauge" -> jsonObject("value" -> a.value.toString))

  def encodeHistogram(a: MetricType.DoubleHistogram) =
    jsonObject(
      "Histogram" -> jsonObject(
        "buckets" -> jsonArray(
          a.buckets,
          (b: (Double, Long)) => s"[${if (b._1 == Double.MaxValue) "Inf" else b._1.toString},${b._2.toString}]"
        ),
        "count"   -> a.count.toString,
        "sum"     -> a.sum.toString
      )
    )

  def encodeQuantile(q: Double, v: Option[Double], error: Double) =
    jsonObject(
      "q"     -> q.toString,
      "error" -> error.toString,
      "value" -> (v match {
        case None    => "NaN"
        case Some(v) => v.toString
      })
    )

  def encodeSummary(a: MetricType.Summary) =
    jsonObject(
      "Summary" -> jsonObject(
        "quantiles" -> jsonArray(a.quantiles, (q: (Double, Option[Double])) => encodeQuantile(q._1, q._2, a.error)),
        "count"     -> a.count.toString,
        "sum"       -> a.sum.toString
      )
    )
}
