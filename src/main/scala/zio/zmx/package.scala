package zio

import zio.zmx.internal.ConcurrentState
import zio.zmx.metrics.{ MetricKey, MetricListener }
import zio.zmx.state.MetricState

package object zmx {

  type Label = (String, String)

  private[zmx] def installListener(l: MetricListener): Unit = metricState.installListener(l)
  private[zmx] def removeListener(l: MetricListener): Unit  = metricState.removeListener(l)
  private[zmx] def snapshot(): Map[MetricKey, MetricState]  = metricState.snapshot()

  private[zmx] lazy val metricState: ConcurrentState =
    new ConcurrentState
}
