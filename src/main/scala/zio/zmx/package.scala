package zio

import zio.zmx.metrics.MetricKey
import zio.zmx.state.MetricState
import zio.zmx.internal.ConcurrentState

package object zmx {

  type Label = (String, String)

  def snapshot(): Map[MetricKey, MetricState] = metricState.snapshot()

  private[zmx] lazy val metricState: ConcurrentState =
    new ConcurrentState
}
