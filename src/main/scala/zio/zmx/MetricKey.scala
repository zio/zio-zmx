package zio.zmx

import zio.Chunk
import zio.zmx.Label

import java.time.Duration

/**
 * A `MetricKey` is a unique key associated with each metric. The key is based
 * on a combination of the metric type, the name and labels associated with
 * the metric, and any other information to describe a a metric, such as the
 * boundaries of a histogram. In this way, it is impossible to ever create
 * metrics with conflicting keys.
 */
sealed trait MetricKey

object MetricKey {
  final case class Counter(name: String, tags: Label*)                              extends MetricKey
  final case class Gauge(name: String, tags: Label*)                                extends MetricKey
  final case class Histogram(name: String, boundaries: Chunk[Double], tags: Label*) extends MetricKey
  final case class Summary(
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double],
    tags: Label*
  )                                                                                 extends MetricKey
}
