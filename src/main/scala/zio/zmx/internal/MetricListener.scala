package zio.zmx.internal

import zio._
import zio.zmx.state.MetricState

/**
 * A `MetricListener` is capable of taking some action in response to a metric
 * being recorded, such as sending that metric to a third party service.
 */
trait MetricListener {
  def gaugeChanged(key: MetricKey.Gauge, value: Double, delta: Double): UIO[Unit]

  def counterChanged(key: MetricKey.Counter, absValue: Double, delta: Double): UIO[Unit]

  def histogramChanged(key: MetricKey.Histogram, value: MetricState): UIO[Unit]

  def summaryChanged(key: MetricKey.Summary, value: MetricState): UIO[Unit]

  def setChanged(key: MetricKey.SetCount, value: MetricState): UIO[Unit]
}
