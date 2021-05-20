package zio

import zio.zmx.state.MetricState
import zio.zmx.internal.ConcurrentState
import zio.zmx.metrics.MetricListener

package object zmx {

  type Label = (String, String)

  def installListener(l: MetricListener): Unit = metricState.installListener(l)
  def removeListener(l: MetricListener): Unit  = metricState.removeListener(l)
  def snapshot(): Chunk[MetricState]           = metricState.snapshot()

  private[zmx] lazy val metricState: ConcurrentState =
    new ConcurrentState
}
