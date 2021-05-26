package zio.zmx.metrics

import zio._
import zio.zmx._

import java.time.{ Duration, Instant }

/**
 * A `MetricAspect` is able to add collection of metrics to a `ZIO` effect
 * without changing its environment, error, or value types. Aspects are the
 * idiomatic way of adding collection of metrics to effects.
 */
trait MetricAspect[-A] {
  def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1]
}

object MetricAspect {

  /**
   * A metric aspect that increments the specified counter each time the
   * effect it is applied to succeeds.
   */
  def count(name: String, tags: Label*): MetricAspect[Any] = {
    val key     = MetricKey.Counter(name, tags: _*)
    val counter = metricState.getCounter(key)
    new MetricAspect[Any] {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tap(_ => counter.increment(1.0d))
    }
  }

  /**
   * A metric aspect that increments the specified counter each time the
   * effect it is applied to fails.
   */

  def countErrors(name: String, tags: Label*): MetricAspect[Any] = {
    val key     = MetricKey.Counter(name, tags: _*)
    val counter = metricState.getCounter(key)
    new MetricAspect[Any] {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tapError(_ => counter.increment(1.0d))
    }
  }

  def gauge(name: String, tags: Label*): MetricAspect[Double] = {
    val key   = MetricKey.Gauge(name, tags: _*)
    val gauge = metricState.getGauge(key)
    new MetricAspect[Double] {
      def apply[R, E, A <: Double](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tap(gauge.set)
    }
  }

  def gaugeWith[A](name: String, tags: Label*)(f: A => ZIO[Any, Nothing, Double]): MetricAspect[A] = {
    val key   = MetricKey.Gauge(name, tags: _*)
    val gauge = metricState.getGauge(key)
    new MetricAspect[A] {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => f(a).flatMap(gauge.set(_)))
    }
  }

  def gaugeRelative(name: String, tags: Label*): MetricAspect[Double] = {
    val key   = MetricKey.Gauge(name, tags: _*)
    val gauge = metricState.getGauge(key)
    new MetricAspect[Double] {
      def apply[R, E, A <: Double](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tap(gauge.set)
    }
  }

  def gaugeRelativeWith[A](name: String, tags: Label*)(f: A => ZIO[Any, Nothing, Double]): MetricAspect[A] = {
    val key   = MetricKey.Gauge(name, tags: _*)
    val gauge = metricState.getGauge(key)
    new MetricAspect[A] {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => f(a).flatMap(gauge.set(_)))
    }
  }

  def observeInHistogram(name: String, boundaries: Chunk[Double], tags: Label*): MetricAspect[Double] = {
    val key       = MetricKey.Histogram(name, boundaries, tags: _*)
    val histogram = metricState.getHistogram(key)
    new MetricAspect[Double] {
      def apply[R, E, A <: Double](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tap(histogram.observe)
    }
  }

  def observeInHistogramWith[A](name: String, boundaries: Chunk[Double], tags: Label*)(
    f: A => ZIO[Any, Nothing, Double]
  ): MetricAspect[A] = {
    val key       = MetricKey.Histogram(name, boundaries, tags: _*)
    val histogram = metricState.getHistogram(key)
    new MetricAspect[A] {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => f(a).flatMap(histogram.observe(_)))
    }
  }

  def observeInSummary(
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double],
    tags: Label*
  ): MetricAspect[Double] = {
    val key     = MetricKey.Summary(name, maxAge, maxSize, error, quantiles, tags: _*)
    val summary = metricState.getSummary(key)
    new MetricAspect[Double] {
      def apply[R, E, A <: Double](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tap(summary.observe(_, Instant.now()))
    }
  }

  def observeInSummaryWith[A](
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double],
    tags: Label*
  )(f: A => ZIO[Any, Nothing, Double]): MetricAspect[A] = {
    val key     = MetricKey.Summary(name, maxAge, maxSize, error, quantiles, tags: _*)
    val summary = metricState.getSummary(key)
    new MetricAspect[A] {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => f(a).flatMap(summary.observe(_, Instant.now())))
    }
  }

  def observeString(name: String, setTag: String, tags: Label*): MetricAspect[String] = {
    val key      = MetricKey.SetCount(name, setTag, tags: _*)
    val setCount = metricState.getSetCount(key)
    new MetricAspect[String] {
      def apply[R, E, A <: String](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tap(setCount.observe)
    }
  }

  def observeStringWith[A](name: String, setTag: String, tags: Label*)(
    f: A => ZIO[Any, Nothing, String]
  ): MetricAspect[A] = {
    val key      = MetricKey.SetCount(name, setTag, tags: _*)
    val setCount = metricState.getSetCount(key)
    new MetricAspect[A] {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => f(a).flatMap(setCount.observe(_)))
    }
  }
}
