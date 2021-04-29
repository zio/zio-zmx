package zio.zmx.internal

import zio.zmx.Label
import zio.Chunk

import java.time.Duration

sealed trait MetricKey

object MetricKey {

  final case class Counter(name: String, tags: Label*) extends MetricKey

  final case class Gauge(name: String, tags: Label*) extends MetricKey

  final case class Histogram(name: String, boundaries: Chunk[Double], tags: Label*) extends MetricKey

  final case class Summary(name: String, maxAge: Duration, maxSize: Int, quantiles: Chunk[Double], tags: Label*)
      extends MetricKey
}
