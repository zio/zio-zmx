package zio.zmx.metrics

import java.time.Instant

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
  def count(key: MetricKey.Counter): MetricAspect = {
    val counter = metricState.getCounter(key)
    new MetricAspect {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tap(_ => counter.increment(1.0d))
    }
  }

  /**
   * A metric aspect that increments the specified counter each time the
   * effect it is applied to fails.
   */

  def countErrors(key: MetricKey.Counter): MetricAspect = {
    val counter = metricState.getCounter(key)

    new MetricAspect {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tapError(_ => counter.increment(1.0d))
    }
  }

  /*
   * TODO: Do we need a function to pull out the actual value we would like to observe
   */
  def gauge[A](key: MetricKey.Gauge)(f: A => ZIO[Any, Nothing, Double]): MetricAspect = {
    val gauge = metricState.getGauge(key)
    new MetricAspect {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => f(a).flatMap(gauge.set(_)))
    }
  }

  def gaugeRelative[A](key: MetricKey.Gauge)(f: A => ZIO[Any, Nothing, Double]): MetricAspect = {
    val gauge = metricState.getGauge(key)

    new MetricAspect {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => f(a).flatMap(gauge.set(_)))
    }
  }

  def observeInHistogram[A](key: MetricKey.Histogram)(f: A => ZIO[Any, Nothing, Double]): MetricAspect = {
    val histogram = metricState.getHistogram(key)
    new MetricAspect {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => f(a).flatMap(histogram.observe(_)))
    }
  }

  def observeInSummary[A](key: MetricKey.Summary)(f: A => ZIO[Any, Nothing, Double]): MetricAspect = {
    val summary = metricState.getSummary(key)
    new MetricAspect {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => f(a).flatMap(summary.observe(_, Instant.now())))
    }
  }

  def observeString[A](key: MetricKey.SetCount)(f: A => ZIO[Any, Nothing, String]): MetricAspect = {
    val setCount = metricState.getSetCount(key)

    new MetricAspect {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => f(a).flatMap(setCount.observe(_)))
    }
  }
}
