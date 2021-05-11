package zio.zmx.metrics

import zio._
import zio.zmx._

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
  def count(key: MetricKey.Counter): MetricAspect =
    new MetricAspect {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tap(_ => incrementCounter(key, 1.0d))
    }

  /**
   * A metric aspect that increments the specified counter each time the
   * effect it is applied to fails.
   */

  def countErrors(key: MetricKey.Counter): MetricAspect =
    new MetricAspect {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tapError(_ => incrementCounter(key, 1.0d))
    }

  def gauge[R, E, A](key: MetricKey.Gauge)(f: A => ZIO[Any, Nothing, Double]): MetricAspect =
    new MetricAspect {
      def apply(zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tap(a => f(a).flatMap(setGauge(key, _)))
    }

  def gaugeRelative[R, E, A](key: MetricKey.Gauge)(f: A => ZIO[Any, Nothing, Double]): MetricAspect =
    new MetricAspect {
      def apply(zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tap(a => f(a).flatMap(adjustGauge(key, _)))
    }

  def observeDouble[R, E, A](key: MetricKey)(f: A => ZIO[Any, Nothing, Double]): MetricAspect =
    new MetricAspect {
      def apply(zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tap(a =>
          f(a).flatMap { v =>
            key match {
              case gk: MetricKey.Gauge     => setGauge(gk, v.asInstanceOf[Double])
              case hk: MetricKey.Histogram => observeInHistogram(hk, v.asInstanceOf[Double])
              case sk: MetricKey.Summary   => observeInSummary(sk, v.asInstanceOf[Double])
              case _                       => ZIO.unit
            }
          }
        )
    }

  def observeOccurence[R, E, A](key: MetricKey.Occurence)(f: A => ZIO[Any, Nothing, String]): MetricAspect =
    new MetricAspect {
      def apply(zio: ZIO[R, E, A]): ZIO[R, E, A] = zio.tap(a => f(a).flatMap(observeString(key, _)))
    }
}
