package zio.zmx.statsd

import java.text.DecimalFormat

import zio.metrics._

abstract private[zmx] class StatsdListener(client: StatsdClient) extends MetricListener {

  import zio.internal.metrics.metricRegistry

  override def unsafeUpdate[Type <: MetricKeyType](key: MetricKey[Type]): key.keyType.In => Unit = (key.keyType match {
    case _: MetricKeyType.Counter   => unsafeUpdateCounter(key.asInstanceOf[MetricKey[MetricKeyType.Counter]])
    case _: MetricKeyType.Gauge     => unsafeUpdateGauge(key.asInstanceOf[MetricKey[MetricKeyType.Gauge]])
    case _: MetricKeyType.Histogram => unsafeUpdateHistogram(key.asInstanceOf[MetricKey[MetricKeyType.Histogram]])
    case _: MetricKeyType.Summary   => unsafeUpdateSummary(key.asInstanceOf[MetricKey[MetricKeyType.Summary]])
    case _: MetricKeyType.Frequency => unsafeUpdateFrequency(key.asInstanceOf[MetricKey[MetricKeyType.Frequency]])
    case _                          => ()
  }).asInstanceOf[key.keyType.In => Unit]

  // For a counter we only send the last observed value to statsd
  private def unsafeUpdateCounter(key: MetricKey[MetricKeyType.Counter]): Double => Unit = { delta =>
    send(encode(key.name, delta, "c", key.tags))
  }

  // For a gauge we send the current value to statsd
  private def unsafeUpdateGauge(key: MetricKey[MetricKeyType.Gauge]): Double => Unit = { _ =>
    val current: Double = metricRegistry.get(key).get().value
    send(encode(key.name, current, "g", key.tags))

  }

  // A Histogram is reported to statsd as a set of related gauges, distinguished by an additional label
  private def unsafeUpdateHistogram(key: MetricKey[MetricKeyType.Histogram]): Double => Unit = { _ =>
    // get the current state from the concurrent registry and evaluate it
    val current: MetricState.Histogram = metricRegistry.get(key).get()
    val buf                            = new StringBuilder()

    current.buckets
      .foreach { case (boundary, count) =>
        val bucket = if (boundary < Double.MaxValue) boundary.toString() else "Inf"
        if (buf.nonEmpty) buf.append("\n")
        buf.append(encode(key.name, count.doubleValue, "g", key.tags + MetricLabel("le", bucket)))
      }

    send(buf.toString())
  }

  // A Summary is reported to statsd as a set of related gauges, distinguished by an additional label
  // for the quantile and another label for the error margin
  private def unsafeUpdateSummary(key: MetricKey[MetricKeyType.Summary]): Double => Unit = { _ =>
    val current: MetricState.Summary = metricRegistry.get(key).get()
    val buf                          = new StringBuilder()

    current.quantiles
      .foreach { case (q, v) =>
        v.foreach { v =>
          if (buf.nonEmpty) buf.append("\n")

          buf.append(
            encode(
              key.name,
              v,
              "g",
              key.tags ++ Set(MetricLabel("quantile", q.toString()), MetricLabel("error", current.count.toString())),
            ),
          )
        }
      }

    send(buf.toString())
  }

  // For each individual observed String we are going to report a counter to statsd with an
  // additional label with key "bucket" and the observed String as a value
  private def unsafeUpdateFrequency(key: MetricKey[MetricKeyType.Frequency]): String => Unit = { v =>
    // We are sending the increment by 1 for the counter related to the observed occurrence
    send(encode(key.name, 1.0d, "c", key.tags + MetricLabel("bucket", v)))
  }

  private def send(datagram: String): Unit = {
    client.write(datagram)
    ()
  }

  private def encode(
    name: String,
    value: Double,
    metricType: String,
    tags: Set[MetricLabel],
  ): String = {
    val tagString = encodeTags(tags)
    s"$name:${format.format(value)}|$metricType$tagString"
  }

  private def encodeTags(tags: Set[MetricLabel]): String =
    if (tags.isEmpty) ""
    else tags.map(t => s"${t.key}:${t.value}").mkString("|#", ",", "")

  private lazy val format = new DecimalFormat("0.################")

}

object StatsdListener {
  def make(client: StatsdClient) =
    new StatsdListener(client) {}
}
