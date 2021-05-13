package zio.zmx.metrics

import java.time.Instant

import zio._
import zio.zmx._

/**
 * A `MetricAspect` is able to add collection of metrics to a `ZIO` effect
 * without changing its environment, error, or value types. Aspects are the
 * idiomatic way of adding collection of metrics to effects.
 */
trait MetricAspect[-T] {
  def apply[R, E, A](zio: ZIO[R, E, A])(implicit ev: A <:< T): ZIO[R, E, A]
}

object MetricAspect {

  /**
   * A metric aspect that increments the specified counter each time the
   * effect it is applied to succeeds.
   */
  def count(key: MetricKey.Counter): MetricAspect[Any] = {
    val counter = metricState.getCounter(key)
    new MetricAspect[Any] {
      def apply[R, E, A](zio: ZIO[R, E, A])(implicit ev: A <:< Any): ZIO[R, E, A] =
        zio.tap(_ => counter.increment(1.0d))
    }
  }

  /**
   * A metric aspect that increments the specified counter each time the
   * effect it is applied to fails.
   */

  def countErrors(key: MetricKey.Counter): MetricAspect[Any] = {
    val counter = metricState.getCounter(key)

    new MetricAspect[Any] {
      def apply[R, E, A](zio: ZIO[R, E, A])(implicit ev: A <:< Any): ZIO[R, E, A] =
        zio.tapError(_ => counter.increment(1.0d))
    }
  }

  def gauge[T](key: MetricKey.Gauge)(f: T => ZIO[Any, Nothing, Double]): MetricAspect[T] = {
    val gauge = metricState.getGauge(key)
    new MetricAspect[T] {
      def apply[R, E, A](zio: ZIO[R, E, A])(implicit ev: A <:< T): ZIO[R, E, A] =
        zio.tap(a => f(a).flatMap(gauge.set(_)))
    }
  }

  def gaugeRelative[T](key: MetricKey.Gauge)(f: T => ZIO[Any, Nothing, Double]): MetricAspect[T] = {
    val gauge = metricState.getGauge(key)

    new MetricAspect[T] {
      def apply[R, E, A](zio: ZIO[R, E, A])(implicit ev: A <:< T): ZIO[R, E, A] =
        zio.tap(a => f(a).flatMap(gauge.set(_)))
    }
  }

  def observeInHistogram[T](key: MetricKey.Histogram)(f: T => ZIO[Any, Nothing, Double]): MetricAspect[T] = {
    val histogram = metricState.getHistogram(key)
    new MetricAspect[T] {
      def apply[R, E, A](zio: ZIO[R, E, A])(implicit ev: A <:< T): ZIO[R, E, A] =
        zio.tap(a => f(a).flatMap(histogram.observe(_)))
    }
  }

  def observeInSummary[T](key: MetricKey.Summary)(f: T => ZIO[Any, Nothing, Double]): MetricAspect[T] = {
    val summary = metricState.getSummary(key)
    new MetricAspect[T] {
      def apply[R, E, A](zio: ZIO[R, E, A])(implicit ev: A <:< T): ZIO[R, E, A] =
        zio.tap(a => f(a).flatMap(summary.observe(_, Instant.now())))
    }
  }

  def observeString[T](key: MetricKey.SetCount)(f: T => ZIO[Any, Nothing, String]): MetricAspect[T] = {
    val setCount = metricState.getSetCount(key)

    new MetricAspect[T] {
      def apply[R, E, A](zio: ZIO[R, E, A])(implicit ev: A <:< T): ZIO[R, E, A] =
        zio.tap(a => f(a).flatMap(setCount.observe(_)))
    }
  }
}
