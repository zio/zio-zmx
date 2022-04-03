package zio.zmx.statsd

import java.text.DecimalFormat

import zio._
import zio.metrics._

abstract private[zmx] class StatsdListener(client: StatsdClient) extends MetricListener {

  override def unsafeUpdate[Type <: MetricKeyType](key: MetricKey[Type]): key.keyType.In => Unit = (key.keyType match {
    case kt: MetricKeyType.Counter => unsafeUpdateCounter(key.asInstanceOf[MetricKey[MetricKeyType.Counter]])
    case _                         => ()
  }).asInstanceOf[key.keyType.In => Unit]

  private def unsafeUpdateCounter(key: MetricKey[MetricKeyType.Counter]): Double => Unit =
    value => () // Do something here

  // private def state[Type <: MetricKeyType](key: MetricKey[Type]): key.keyType.In => MetricState[_] = ???

  // override def unsafeGaugeObserved(key: MetricKey.Gauge, value: Double, delta: Double): Unit =
  //   send(encodeGauge(key, value, delta))

  // override def unsafeCounterObserved(key: MetricKey.Counter, value: Double, delta: Double): Unit =
  //   send(encodeCounter(key, value, delta))

  // override def unsafeHistogramObserved(key: MetricKey.Histogram, value: Double): Unit =
  //   MetricClient.unsafeState(key).map(_.details) match {
  //     case Some(value: MetricType.DoubleHistogram) => send(encodeHistogram(key, value))
  //     case _                                       =>
  //   }

  // override def unsafeSummaryObserved(key: MetricKey.Summary, value: Double): Unit =
  //   MetricClient.unsafeState(key).map(_.details) match {
  //     case Some(value: MetricType.Summary) => send(encodeSummary(key, value))
  //     case _                               =>
  //   }

  // override def unsafeSetObserved(key: MetricKey.SetCount, value: String): Unit =
  //   MetricClient.unsafeState(key).map(_.details) match {
  //     case Some(value: MetricType.SetCount) => send(encodeSet(key, value))
  //     case _                                =>
  //   }

  // private def send(datagram: String): Unit = {
  //   client.write(datagram)
  //   ()
  // }

  // private def encodeCounter(key: MetricKey.Counter, value: Double, delta: Double) = {
  //   val _ = value
  //   encode(key.name, delta, "c", key.tags)
  // }

  // private def encodeGauge(key: MetricKey.Gauge, value: Double, delta: Double) = {
  //   val _ = delta
  //   encode(key.name, value, "g", key.tags)
  // }

  // private def encodeHistogram(key: MetricKey.Histogram, value: MetricType.DoubleHistogram) = {

  // get the current state from the concurrent registry and evaluate it

  // value.buckets.map { case (boundary, count) =>
  //   val bucket = if (boundary < Double.MaxValue) boundary.toString() else "Inf"
  //   encode(key.name, count.doubleValue, "g", key.tags ++ Chunk(MetricLabel("le", bucket)))
  // }.mkString("\n")

  // private def encodeSummary(key: MetricKey.Summary, value: MetricType.Summary) =
  //   value.quantiles.collect { case (q, Some(v)) => (q, v) }.map { case (q, v) =>
  //     encode(
  //       key.name,
  //       v,
  //       "g",
  //       key.tags ++ Chunk(MetricLabel("quantile", q.toString()), MetricLabel("error", key.error.toString()))
  //     )
  //   }.mkString("\n")

  // private def encodeSet(key: MetricKey.SetCount, value: MetricType.SetCount) =
  //   value.occurrences.map { case (word, count) =>
  //     encode(key.name, count.doubleValue(), "g", key.tags ++ Chunk(MetricLabel(key.setTag, word)))
  //   }.mkString("\n")

  // private def encode(
  //   name: String,
  //   value: Double,
  //   metricType: String,
  //   tags: Chunk[MetricLabel]
  // ): String = {
  //   val tagString = encodeTags(tags)
  //   s"${name}:${format.format(value)}|${metricType}${tagString}"
  // }

  // private def encodeTags(tags: Chunk[MetricLabel]): String =
  //   if (tags.isEmpty) ""
  //   else tags.map(t => s"${t.key}:${t.value}").mkString("|#", ",", "")

  // private lazy val format = new DecimalFormat("0.################")

}

object StatsdListener {
  def make(client: StatsdClient) =
    new StatsdListener(client) {}
}
