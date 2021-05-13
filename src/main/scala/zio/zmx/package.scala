package zio

import zio.zmx.internal.ConcurrentState

package object zmx {

  type Label = (String, String)

  private[zmx] val metricState: ConcurrentState =
    new ConcurrentState
}
