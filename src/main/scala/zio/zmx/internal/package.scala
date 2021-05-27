package zio.zmx

import zio.zmx.state.MetricState

package object internal {

  def installListener(l: MetricListener): Unit =
    metricState.installListener(l)

  def removeListener(l: MetricListener): Unit =
    metricState.removeListener(l)

  def snapshot(): Map[MetricKey, MetricState] =
    metricState.snapshot()

  private[zmx] lazy val metricState: ConcurrentState =
    new ConcurrentState
}
