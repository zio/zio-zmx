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
trait MetricAspect[-A] { self =>

  protected type Key

  protected type Metric

  protected def get(key: Key): Metric

  protected def key: Key

  protected def metric: Metric

  protected def tag(key: Key)(label: Label, labels: Label*): Key

  protected def track[R, E, A1 <: A](zio: ZIO[R, E, A1])(metric: Metric): ZIO[R, E, A1]

  final def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
    track(zio)(metric)

  def tag(label: Label, labels: Label*): MetricAspect[A] =
    new MetricAspect[A] {
      type Key    = self.Key
      type Metric = self.Metric
      protected def get(key: Key): Metric                                                   =
        self.get(key)
      val key: Key                                                                          =
        tag(self.key)(label, labels: _*)
      val metric: Metric                                                                    =
        get(key)
      protected def tag(key: Key)(label: Label, labels: Label*): Key                        =
        self.tag(key)(label, labels: _*)
      protected def track[R, E, A1 <: A](zio: ZIO[R, E, A1])(metric: Metric): ZIO[R, E, A1] =
        self.track(zio)(metric)
    }
}

object MetricAspect {

  /**
   * A metric aspect that increments the specified counter each time the
   * effect it is applied to succeeds.
   */
  def count(name: String, tags: Label*): MetricAspect[Any] =
    new CounterAspect[Any] {
      val key: MetricKey.Counter                                            = MetricKey.Counter(name, tags: _*)
      val metric                                                            = metricState.getCounter(key)
      def track[R, E, A](zio: ZIO[R, E, A])(counter: Counter): ZIO[R, E, A] =
        zio.tap(_ => counter.increment(1.0d))
    }

  /**
   * A metric aspect that increments the specified counter by a given value.
   */
  def countValue(name: String, tags: Label*) =
    new CounterAspect[Double] {
      val key                                                                            = MetricKey.Counter(name, tags: _*)
      val metric                                                                         = metricState.getCounter(key)
      def track[R, E, A1 <: Double](zio: ZIO[R, E, A1])(counter: Counter): ZIO[R, E, A1] =
        zio.tap(counter.increment(_))
    }

  /**
   * A metric aspect that increments the specified counter by a given value.
   */
  def countValueWith[A](name: String, tags: Label*)(f: A => Double) =
    new CounterAspect[A] {
      val key                                                                       = MetricKey.Counter(name, tags: _*)
      val metric                                                                    = metricState.getCounter(key)
      def track[R, E, A1 <: A](zio: ZIO[R, E, A1])(counter: Counter): ZIO[R, E, A1] =
        zio.tap(v => counter.increment(f(v)))
    }

  /**
   * A metric aspect that increments the specified counter each time the
   * effect it is applied to fails.
   */

  def countErrors(name: String, tags: Label*): MetricAspect[Any] =
    new CounterAspect[Any] {
      val key                                                               = MetricKey.Counter(name, tags: _*)
      val metric                                                            = metricState.getCounter(key)
      def track[R, E, A](zio: ZIO[R, E, A])(counter: Counter): ZIO[R, E, A] =
        zio.tapError(_ => counter.increment(1.0d))
    }

  /**
   * A metric aspect that sets a gauge each time the effect it is applied to
   * succeeds.
   */
  def setGauge(name: String, tags: Label*): MetricAspect[Double] =
    new GaugeAspect[Double] {
      val key                                                                     = MetricKey.Gauge(name, tags: _*)
      val metric                                                                  = metricState.getGauge(key)
      def track[R, E, A <: Double](zio: ZIO[R, E, A])(gauge: Gauge): ZIO[R, E, A] =
        zio.tap(gauge.set)
    }

  /**
   * A metric aspect that sets a gauge each time the effect it is applied to
   * succeeds, using the specified function to transform the value returned by
   * the effect to the value to set the gauge to.
   */
  def setGaugeWith[A](name: String, tags: Label*)(f: A => Double): MetricAspect[A] =
    new GaugeAspect[A] {
      val key                                                                   = MetricKey.Gauge(name, tags: _*)
      val metric                                                                = metricState.getGauge(key)
      def track[R, E, A1 <: A](zio: ZIO[R, E, A1])(gauge: Gauge): ZIO[R, E, A1] =
        zio.tap(a => gauge.set(f(a)))
    }

  /**
   * A metric aspect that adjusts a gauge each time the effect it is applied
   * to succeeds.
   */
  def adjustGauge(name: String, tags: Label*): MetricAspect[Double] =
    new GaugeAspect[Double] {
      val key                                                                     = MetricKey.Gauge(name, tags: _*)
      val metric                                                                  = metricState.getGauge(key)
      def track[R, E, A <: Double](zio: ZIO[R, E, A])(gauge: Gauge): ZIO[R, E, A] =
        zio.tap(gauge.set)
    }

  /**
   * A metric aspect that adjusts a gauge each time the effect it is applied
   * to succeeds, using the specified function to transform the value returned
   * by the effect to the value to adjust the gauge with.
   */
  def adjustGaugeWith[A](name: String, tags: Label*)(f: A => Double): MetricAspect[A] =
    new GaugeAspect[A] {
      val key                                                                   = MetricKey.Gauge(name, tags: _*)
      val metric                                                                = metricState.getGauge(key)
      def track[R, E, A1 <: A](zio: ZIO[R, E, A1])(gauge: Gauge): ZIO[R, E, A1] =
        zio.tap(a => gauge.set(f(a)))
    }

  /**
   * A metric aspect that adds a value to a histogram each time the effect it
   * is applied to succeeds.
   */
  def observeHistogram(name: String, boundaries: Chunk[Double], tags: Label*): MetricAspect[Double] =
    new HistogramAspect[Double] {
      val key                                                                             = MetricKey.Histogram(name, boundaries, tags: _*)
      val metric                                                                          = metricState.getHistogram(key)
      def track[R, E, A <: Double](zio: ZIO[R, E, A])(histogram: Histogram): ZIO[R, E, A] =
        zio.tap(histogram.observe)
    }

  /**
   * A metric aspect that adds a value to a histogram each time the effect it
   * is applied to succeeds, using the specified function to transform the
   * value returned by the effect to the value to add to the histogram.
   */
  def observeHistogramWith[A](name: String, boundaries: Chunk[Double], tags: Label*)(
    f: A => Double
  ): MetricAspect[A] =
    new HistogramAspect[A] {
      val key                                                                           = MetricKey.Histogram(name, boundaries, tags: _*)
      val metric                                                                        = metricState.getHistogram(key)
      def track[R, E, A1 <: A](zio: ZIO[R, E, A1])(histogram: Histogram): ZIO[R, E, A1] =
        zio.tap(a => histogram.observe(f(a)))
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
  ): MetricAspect[Double] =
    new SummaryAspect[Double] {
      val key                                                                         = MetricKey.Summary(name, maxAge, maxSize, error, quantiles, tags: _*)
      val metric                                                                      = metricState.getSummary(key)
      def track[R, E, A <: Double](zio: ZIO[R, E, A])(summary: Summary): ZIO[R, E, A] =
        zio.tap(summary.observe(_, Instant.now()))
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
  )(f: A => Double): MetricAspect[A] =
    new SummaryAspect[A] {
      val key                                                                       = MetricKey.Summary(name, maxAge, maxSize, error, quantiles, tags: _*)
      val metric                                                                    = metricState.getSummary(key)
      def track[R, E, A1 <: A](zio: ZIO[R, E, A1])(summary: Summary): ZIO[R, E, A1] =
        zio.tap(a => summary.observe(f(a), Instant.now()))
    }

  /**
   * A metric aspect that counts the number of occurrences of each distinct
   * value returned by the effect it is applied to.
   */
  def occurrences(name: String, setTag: String, tags: Label*): MetricAspect[String] =
    new SetCountAspect[String] {
      val key                                                                           = MetricKey.SetCount(name, setTag, tags: _*)
      val metric                                                                        = metricState.getSetCount(key)
      def track[R, E, A <: String](zio: ZIO[R, E, A])(setCount: SetCount): ZIO[R, E, A] =
        zio.tap(setCount.observe)
    }

  /**
   * A metric aspect that counts the number of occurrences of each distinct
   * value returned by the effect it is applied to, using the specified
   * function to transform the value returned by the effect to the value to
   * count the occurrences of.
   */
  def occurrencesWith[A](name: String, setTag: String, tags: Label*)(
    f: A => String
  ): MetricAspect[A] =
    new SetCountAspect[A] {
      val key                                                                         = MetricKey.SetCount(name, setTag, tags: _*)
      val metric                                                                      = metricState.getSetCount(key)
      def track[R, E, A1 <: A](zio: ZIO[R, E, A1])(setCount: SetCount): ZIO[R, E, A1] =
        zio.tap(a => setCount.observe(f(a)))
    }

  trait CounterAspect[A] extends MetricAspect[A] {
    protected final type Key    = MetricKey.Counter
    protected final type Metric = Counter
    protected final def get(key: MetricKey.Counter): Counter                                         =
      metricState.getCounter(key)
    protected final def tag(key: MetricKey.Counter)(label: Label, labels: Label*): MetricKey.Counter =
      MetricKey.Counter(key.name, ((key.tags ++ Chunk(label) ++ labels): _*))
  }

  trait GaugeAspect[A] extends MetricAspect[A] {
    protected final type Key    = MetricKey.Gauge
    protected final type Metric = Gauge
    protected final def get(key: MetricKey.Gauge): Gauge                                         =
      metricState.getGauge(key)
    protected final def tag(key: MetricKey.Gauge)(label: Label, labels: Label*): MetricKey.Gauge =
      MetricKey.Gauge(key.name, ((key.tags ++ Chunk(label) ++ labels): _*))
  }

  trait HistogramAspect[A] extends MetricAspect[A] {
    protected final type Key    = MetricKey.Histogram
    protected final type Metric = Histogram
    protected final def get(key: MetricKey.Histogram): Histogram                                         =
      metricState.getHistogram(key)
    protected final def tag(key: MetricKey.Histogram)(label: Label, labels: Label*): MetricKey.Histogram =
      MetricKey.Histogram(key.name, key.boundaries, ((key.tags ++ Chunk(label) ++ labels): _*))
  }

  trait SummaryAspect[A] extends MetricAspect[A] {
    protected final type Key    = MetricKey.Summary
    protected final type Metric = Summary
    protected final def get(key: MetricKey.Summary): Summary                                         =
      metricState.getSummary(key)
    protected final def tag(key: MetricKey.Summary)(label: Label, labels: Label*): MetricKey.Summary =
      MetricKey.Summary(
        key.name,
        key.maxAge,
        key.maxSize,
        key.error,
        key.quantiles,
        ((key.tags ++ Chunk(label) ++ labels): _*)
      )
  }

  trait SetCountAspect[A] extends MetricAspect[A] {
    protected final type Key    = MetricKey.SetCount
    protected final type Metric = SetCount
    protected final def get(key: MetricKey.SetCount): SetCount                                         =
      metricState.getSetCount(key)
    protected final def tag(key: MetricKey.SetCount)(label: Label, labels: Label*): MetricKey.SetCount =
      MetricKey.SetCount(key.name, key.setTag, ((key.tags ++ Chunk(label) ++ labels): _*))
  }
}
