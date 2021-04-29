package zio.zmx

import zio._

package object metrics {

  final implicit class MetricsSyntax[-R, +E, +A](private val self: ZIO[R, E, A]) extends AnyVal {

    /**
     * Syntax for applying metric aspects.
     */
    def @@(aspect: MetricAspect): ZIO[R, E, A] =
      aspect(self)
  }
}
