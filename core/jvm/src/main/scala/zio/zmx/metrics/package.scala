package zio.zmx

import zio._

package object metrics {

  implicit final class MetricsSyntax[-R, +E, +A](private val self: ZIO[R, E, A]) extends AnyVal {

    /**
     * Syntax for applying metric aspects.
     */
    def @@(aspect: MetricAspect[A]): ZIO[R, E, A] =
      aspect(self)
  }
}
