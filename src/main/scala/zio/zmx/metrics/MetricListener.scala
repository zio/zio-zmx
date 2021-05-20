package zio.zmx.metrics

import zio._

/**
 * A `MetricListener` is capable of taking some action in response to a metric
 * being recorded, such as sending that metric to a third party service.
 */
trait MetricListener {
  def setGauge(key: MetricKey.Gauge, value: Double): UIO[Unit]
  //def adjustGauge(key: MetricKey.Gauge, value: Double): UIO[Unit]

  def setCounter(key: MetricKey.Counter, value: Double): UIO[Unit]

  def observeHistogram(key: MetricKey.Histogram, value: Double): UIO[Unit]

  def observeSummary(key: MetricKey.Summary, value: Double): UIO[Unit]

}
