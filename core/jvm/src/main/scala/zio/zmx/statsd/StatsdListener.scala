package zio.zmx.statsd

import java.text.DecimalFormat

import zio.zmx.Label
import zio.zmx.internal.{ MetricKey, MetricListener }
import zio.zmx.state.MetricType
import zio.zmx.state.MetricState

private[zmx] abstract class StatsdListener(client: StatsdClient) extends MetricListener {

  override def gaugeChanged(key: MetricKey.Gauge, value: Double, delta: Double): Unit =
    send(encodeGauge(key, value, delta))

  override def counterChanged(key: MetricKey.Counter, value: Double, delta: Double): Unit =
    send(encodeCounter(key, value, delta))

  override def histogramChanged(key: MetricKey.Histogram, value: MetricState): Unit =
    value.details match {
      case value: MetricType.DoubleHistogram => send(encodeHistogram(key, value))
      case _                                 =>
    }

  override def summaryChanged(key: MetricKey.Summary, value: MetricState): Unit =
    value.details match {
      case value: MetricType.Summary => send(encodeSummary(key, value))
      case _                         =>
    }

  override def setChanged(key: MetricKey.SetCount, value: MetricState): Unit =
    value.details match {
      case value: MetricType.SetCount => send(encodeSet(key, value))
      case _                          =>
    }

  private def send(datagram: String): Unit = {
    client.write(datagram)
    ()
  }

  private def encodeCounter(key: MetricKey.Counter, value: Double, delta: Double) = {
    val _ = value
    encode(key.name, delta, "c", key.tags)
  }

  private def encodeGauge(key: MetricKey.Gauge, value: Double, delta: Double) = {
    val _ = delta
    encode(key.name, value, "g", key.tags)
  }

  private def encodeHistogram(key: MetricKey.Histogram, value: MetricType.DoubleHistogram) =
    value.buckets.map { case (boundary, count) =>
      val bucket = if (boundary < Double.MaxValue) boundary.toString() else "Inf"
      encode(key.name, count.doubleValue, "g", key.tags ++ Seq("le" -> bucket))
    }.mkString("\n")

  private def encodeSummary(key: MetricKey.Summary, value: MetricType.Summary) =
    value.quantiles.collect { case (q, Some(v)) => (q, v) }.map { case (q, v) =>
      encode(key.name, v, "g", key.tags ++ Seq("quantile" -> q.toString(), "error" -> key.error.toString()))
    }.mkString("\n")

  private def encodeSet(key: MetricKey.SetCount, value: MetricType.SetCount) =
    value.occurrences.map { case (word, count) =>
      encode(key.name, count.doubleValue(), "g", key.tags ++ Seq(key.setTag -> word))
    }.mkString("\n")

  private def encode(
    name: String,
    value: Double,
    metricType: String,
    tags: Seq[Label]
  ): String = {
    val tagString = encodeTags(tags)
    s"${name}:${format.format(value)}|${metricType}${tagString}"
  }

  private def encodeTags(tags: Seq[Label]): String =
    if (tags.isEmpty) ""
    else tags.map(t => s"${t._1}:${t._2}").mkString("|#", ",", "")

  private lazy val format = new DecimalFormat("0.################")

}

object StatsdListener {

  def make(client: StatsdClient) =
    new StatsdListener(client) {}
}
