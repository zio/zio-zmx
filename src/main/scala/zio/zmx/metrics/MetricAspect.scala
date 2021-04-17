package zio.zmx.metrics

import zio._

/**
 * A `MetricAspect` is able to add collection of metrics to a `ZIO` effect
 * without changing its environment, error, or value types. Aspects are the
 * idiomatic way of adding collection of metrics to effects.
 */
trait MetricAspect {
  def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A]
}

object MetricAspect {

  /**
   * A metric aspect that increments the specified counter each time the
   * effect it is applied to succeeds.
   */
  def count(name: String): MetricAspect = {
    val counter = Counter(name)
    new MetricAspect {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tap(_ => counter.increment(1.0))
    }
  }

  /**
   * A metric aspect that increments the specified counter each time the
   * effect it is applied to fails.
   */
  def countErrors(name: String): MetricAspect = {
    val counter = Counter(name)
    new MetricAspect {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tapError(_ => counter.increment(1.0))
    }
  }
}
