package zio.zmx.internal

import zio.Chunk
import zio.zmx.Label

import java.time.Duration

/**
 * A `MetricListener` is capable of taking some action in response to a metric
 * being recorded, such as sending that metric to a third party service.
 */
trait MetricListener {
  def adjustGauge(name: String, value: Double, tags: Label*): Unit
  def incrementCounter(name: String, value: Double, tags: Label*): Unit
  def observeHistogram(name: String, boundaries: Chunk[Double], tags: Label*): Unit
  def observeSummary(
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double],
    tags: Label*
  ): Unit
  def setGauge(name: String, value: Double, tags: Label*): Unit
}
