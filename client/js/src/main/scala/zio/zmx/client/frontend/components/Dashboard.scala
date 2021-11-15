package zio.zmx.client.frontend.components

import zio._

object Dashboard {
  sealed trait ViewDirection

  object ViewDirection {
    case object Horizontal extends ViewDirection
    case object Vertical   extends ViewDirection
  }

  sealed trait DashboardView[+T]

  object DashboardView {
    final case class SingleView[+T](config: T)                                   extends DashboardView[T]
    final case class ComposedView[+T](views: Chunk[T], direction: ViewDirection) extends DashboardView[T]
  }
}
