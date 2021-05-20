package zio.zmx.state

import zio._
import zio.zmx.Label
import zio.zmx.metrics.MetricKey
import zio.zmx.state.MetricType._

final case class MetricState(
  name: String,
  help: String,
  labels: Chunk[Label],
  details: MetricType
) {
  override def toString(): String = {
    val lbls = if (labels.isEmpty) "" else labels.map(l => s"${l._1}->${l._2}").mkString("{", ",", "}")
    s"MetricState($name$lbls, $details)"
  }
}

object MetricState {

  // --------- Methods creating and using Prometheus counters
  def counter(key: MetricKey.Counter, help: String, value: Double): MetricState =
    MetricState(key.name, help, Chunk(key.tags: _*), Counter(value))

  // --------- Methods creating and using Prometheus Gauges

  def gauge(
    key: MetricKey.Gauge,
    help: String,
    startAt: Double
  ): MetricState =
    MetricState(key.name, help, Chunk(key.tags: _*), Gauge(startAt))

  // --------- Methods creating and using Prometheus Histograms

  def doubleHistogram(
    key: MetricKey.Histogram,
    help: String,
    buckets: Chunk[(Double, Long)],
    count: Long,
    sum: Double
  ): MetricState =
    MetricState(
      key.name,
      help,
      Chunk(key.tags: _*),
      DoubleHistogram(buckets, count, sum)
    )

  // --------- Methods creating and using Prometheus Histograms

  def summary(
    key: MetricKey.Summary,
    help: String,
    quantiles: Chunk[(Double, Option[Double])],
    count: Long,
    sum: Double
  ): MetricState =
    MetricState(
      key.name,
      help,
      Chunk(key.tags: _*),
      Summary(key.error, quantiles, count, sum)
    )

  def setCount(
    key: MetricKey.SetCount,
    help: String,
    values: Chunk[(String, Long)]
  ): MetricState = MetricState(key.name, help, Chunk(key.tags: _*), SetCount(key.setTag, values))
}
