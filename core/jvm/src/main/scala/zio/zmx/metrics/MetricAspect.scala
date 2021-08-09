package zio.zmx.metrics

import zio._
import zio.zmx._
import zio.zmx.internal._

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
    val key     = MetricKey.Counter(name, Chunk.fromIterable(tags))
    val counter = metricState.getCounter(key)
    new MetricAspect[Any] {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tap(_ => counter.increment(1.0d))
    }
  }

  /**
   * A metric aspect that increments the specified counter by a given value.
   */
  def countValue(name: String, tags: Label*) = {
    val key     = MetricKey.Counter(name, Chunk.fromIterable(tags))
    val counter = metricState.getCounter(key)

    new MetricAspect[Double] {
      def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(counter.increment(_))
    }
  }

  /**
   * A metric aspect that increments the specified counter by a given value.
   */
  def countValueWith[A](name: String, tags: Label*)(f: A => Double) = {
    val key     = MetricKey.Counter(name, Chunk.fromIterable(tags))
    val counter = metricState.getCounter(key)

    new MetricAspect[A] {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(v => counter.increment(f(v)))
    }
  }

  /**
   * A metric aspect that increments the specified counter each time the
   * effect it is applied to fails.
   */

  def countErrors(name: String, tags: Label*): MetricAspect[Any] = {
    val key     = MetricKey.Counter(name, Chunk.fromIterable(tags))
    val counter = metricState.getCounter(key)
    new MetricAspect[Any] {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tapError(_ => counter.increment(1.0d))
    }
  }

  /**
   * A metric aspect that sets a gauge each time the effect it is applied to
   * succeeds.
   */
  def setGauge(name: String, tags: Label*): MetricAspect[Double] = {
    val key   = MetricKey.Gauge(name, Chunk.fromIterable(tags))
    val gauge = metricState.getGauge(key)
    new MetricAspect[Double] {
      def apply[R, E, A <: Double](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tap(gauge.set)
    }
  }

  /**
   * A metric aspect that sets a gauge each time the effect it is applied to
   * succeeds, using the specified function to transform the value returned by
   * the effect to the value to set the gauge to.
   */
  def setGaugeWith[A](name: String, tags: Label*)(f: A => Double): MetricAspect[A] = {
    val key   = MetricKey.Gauge(name, Chunk.fromIterable(tags))
    val gauge = metricState.getGauge(key)
    new MetricAspect[A] {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => gauge.set(f(a)))
    }
  }

  /**
   * A metric aspect that adjusts a gauge each time the effect it is applied
   * to succeeds.
   */
  def adjustGauge(name: String, tags: Label*): MetricAspect[Double] = {
    val key   = MetricKey.Gauge(name, Chunk.fromIterable(tags))
    val gauge = metricState.getGauge(key)
    new MetricAspect[Double] {
      def apply[R, E, A <: Double](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tap(gauge.set)
    }
  }

  /**
   * A metric aspect that adjusts a gauge each time the effect it is applied
   * to succeeds, using the specified function to transform the value returned
   * by the effect to the value to adjust the gauge with.
   */
  def adjustGaugeWith[A](name: String, tags: Label*)(f: A => Double): MetricAspect[A] = {
    val key   = MetricKey.Gauge(name, Chunk.fromIterable(tags))
    val gauge = metricState.getGauge(key)
    new MetricAspect[A] {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => gauge.set(f(a)))
    }
  }

  /**
   * A metric aspect that tracks how long the effect it is applied to takes to
   * complete execution, recording the results in a histogram.
   */
  def observeDurations[A](name: String, boundaries: Chunk[Double], tags: Label*)(
    f: Duration => Double
  ): MetricAspect[A] =
    new MetricAspect[A] {
      val key                                                     = MetricKey.Histogram(name, boundaries, Chunk.fromIterable(tags))
      val histogram                                               = metricState.getHistogram(key)
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.timedWith(ZIO.succeed(System.nanoTime)).flatMap { case (duration, a) =>
          histogram.observe(f(duration)).as(a)
        }
    }

  /**
   * A metric aspect that adds a value to a histogram each time the effect it
   * is applied to succeeds.
   */
  def observeHistogram(name: String, boundaries: Chunk[Double], tags: Label*): MetricAspect[Double] = {
    val key       = MetricKey.Histogram(name, boundaries, Chunk.fromIterable(tags))
    val histogram = metricState.getHistogram(key)
    new MetricAspect[Double] {
      def apply[R, E, A <: Double](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tap(histogram.observe)
    }
  }

  /**
   * A metric aspect that adds a value to a histogram each time the effect it
   * is applied to succeeds, using the specified function to transform the
   * value returned by the effect to the value to add to the histogram.
   */
  def observeHistogramWith[A](name: String, boundaries: Chunk[Double], tags: Label*)(
    f: A => Double
  ): MetricAspect[A] = {
    val key       = MetricKey.Histogram(name, boundaries, Chunk.fromIterable(tags))
    val histogram = metricState.getHistogram(key)
    new MetricAspect[A] {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => histogram.observe(f(a)))
    }
  }

  /**
   * A metric aspect that adds a value to a summary each time the effect it is
   * applied to succeeds.
   */
  def observeSummary(
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double],
    tags: Label*
  ): MetricAspect[Double] = {
    val key     = MetricKey.Summary(name, maxAge, maxSize, error, quantiles, Chunk.fromIterable(tags))
    val summary = metricState.getSummary(key)
    new MetricAspect[Double] {
      def apply[R, E, A <: Double](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tap(summary.observe(_, Instant.now()))
    }
  }

  /**
   * A metric aspect that adds a value to a summary each time the effect it is
   * applied to succeeds, using the specified function to transform the value
   * returned by the effect to the value to add to the summary.
   */
  def observeSummaryWith[A](
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double],
    tags: Label*
  )(f: A => Double): MetricAspect[A] = {
    val key     = MetricKey.Summary(name, maxAge, maxSize, error, quantiles, Chunk.fromIterable(tags))
    val summary = metricState.getSummary(key)
    new MetricAspect[A] {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => summary.observe(f(a), Instant.now()))
    }
  }

  /**
   * A metric aspect that counts the number of occurrences of each distinct
   * value returned by the effect it is applied to.
   */
  def occurrences(name: String, setTag: String, tags: Label*): MetricAspect[String] = {
    val key      = MetricKey.SetCount(name, setTag, Chunk.fromIterable(tags))
    val setCount = metricState.getSetCount(key)
    new MetricAspect[String] {
      def apply[R, E, A <: String](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tap(setCount.observe)
    }
  }

  /**
   * A metric aspect that counts the number of occurrences of each distinct
   * value returned by the effect it is applied to, using the specified
   * function to transform the value returned by the effect to the value to
   * count the occurrences of.
   */
  def occurrencesWith[A](name: String, setTag: String, tags: Label*)(
    f: A => String
  ): MetricAspect[A] = {
    val key      = MetricKey.SetCount(name, setTag, Chunk.fromIterable(tags))
    val setCount = metricState.getSetCount(key)
    new MetricAspect[A] {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => setCount.observe(f(a)))
    }
  }
}
