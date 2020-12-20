package zio.zmx.prometheus

import zio._
import zio.zmx.metrics.MetricsDataModel.Label

sealed abstract class PMetric private (
  val name: String,
  val help: String,
  val labels: Chunk[Label],
  val details: PMetric.Details
)

object PMetric extends WithDoubleOrdering {

  sealed trait Details

  sealed trait Counter extends Details {
    def count: Double
    def inc(v: Double): Counter
  }

  sealed trait Gauge extends Details {
    def value: Double
    def inc(v: Double): Gauge
    def set(v: Double): Gauge
  }

  sealed trait BucketType {
    def boundaries: Chunk[Double]
    def buckets(
      maxAge: java.time.Duration = TimeSeries.defaultMaxAge,
      maxSize: Int = TimeSeries.defaultMaxSize
    ): Chunk[(Double, TimeSeries)] =
      boundaries.map(d => (d, TimeSeries(maxAge, maxSize)))
  }

  object BucketType {

    final case class Manual(limits: Double*) extends BucketType {
      override def boundaries: Chunk[Double] =
        Chunk.fromArray(limits.toArray.sorted(dblOrdering)) ++ Chunk(Double.MaxValue).distinct
    }

    final case class Linear(start: Double, width: Double, count: Int) extends BucketType {
      override def boundaries: Chunk[Double] =
        Chunk.fromArray(0.until(count).map(i => start + i * width).toArray) ++ Chunk(Double.MaxValue)
    }

    final case class Exponential(start: Double, factor: Double, count: Int) extends BucketType {
      override def boundaries: Chunk[Double] =
        Chunk.fromArray(0.until(count).map(i => start * Math.pow(factor, i.toDouble)).toArray) ++ Chunk(Double.MaxValue)
    }
  }

  sealed trait Histogram extends Details {
    def buckets: Chunk[(Double, TimeSeries)]
    def observe(v: Double, t: java.time.Instant): Histogram
    def count: Double
    def sum: Double
  }

  sealed trait Summary extends Details {
    def samples: TimeSeries
    def quantiles: Chunk[Quantile]
    def observe(v: Double, t: java.time.Instant): Summary
    def count: Double
    def sum: Double
  }

  private object Details {
    final case class CounterImpl(override val count: Double) extends Counter {
      override def inc(v: Double): Counter = copy(count = count + v)
    }

    final case class GaugeImpl(override val value: Double) extends Gauge {
      override def inc(v: Double): Gauge = copy(value = value + v)
      override def set(v: Double): Gauge = copy(value = v)
    }

    final case class HistogramImpl(
      override val buckets: Chunk[(Double, TimeSeries)],
      override val count: Double,
      override val sum: Double
    ) extends Histogram { self =>
      override def observe(v: Double, t: java.time.Instant): Histogram = copy(
        buckets = self.buckets.map { case (b, ts) => if (v <= b) (b, ts.observe(v, t)) else (b, ts) },
        count = self.count + 1,
        sum = self.sum + v
      )
    }

    final case class SummaryImpl(
      override val samples: TimeSeries,
      override val quantiles: Chunk[Quantile],
      override val count: Double,
      override val sum: Double
    ) extends Summary { self =>
      override def observe(v: Double, t: java.time.Instant): Summary = copy(
        count = self.count + 1,
        sum = self.sum + v,
        samples = self.samples.observe(v, t)
      )
    }
  }

  // --------- Methods creating and using Prometheus counters
  def counter(name: String, help: String, labels: Chunk[Label] = Chunk.empty): PMetric =
    new PMetric(name, help, labels, Details.CounterImpl(0)) {}

  // The error case is a negative increment and is reflected by returning a None
  def incCounter(m: PMetric, v: Double = 1.0d): Option[PMetric]                        =
    m.details match {
      case c: PMetric.Counter =>
        if (v < 0) None else Some(new PMetric(m.name, m.help, m.labels, c.inc(v)) {})
      case _                  => None
    }

  // --------- Methods creating and using Prometheus Gauges

  def gauge(
    name: String,
    help: String,
    labels: Chunk[Label] = Chunk.empty,
    startAt: Double = 0.0
  ): PMetric                                                  =
    new PMetric(name, help, labels, Details.GaugeImpl(startAt)) {}

  def incGauge(m: PMetric, v: Double = 1.0d): Option[PMetric] =
    m.details match {
      case g: PMetric.Gauge => Some(new PMetric(m.name, m.help, m.labels, g.inc(v)) {})
      case _                => None
    }

  def decGauge(m: PMetric, v: Double = 1.0d): Option[PMetric] = incGauge(m, -v)

  def setGauge(m: PMetric, v: Double): Option[PMetric] =
    m.details match {
      case _: PMetric.Gauge => Some(new PMetric(m.name, m.help, m.labels, Details.GaugeImpl(v)) {})
      case _                => None
    }

  // Set the value of the Gauge to the seconds corresponding to the given Instant
  def setToInstant(m: PMetric, t: java.time.Instant): Option[PMetric] =
    setGauge(m, t.toEpochMilli() / 1000.0d)

  // --------- Methods creating and using Prometheus Histograms

  def histogram(
    name: String,
    help: String,
    labels: Chunk[Label] = Chunk.empty,
    buckets: BucketType,
    maxAge: java.time.Duration = TimeSeries.defaultMaxAge,
    maxSize: Int = TimeSeries.defaultMaxSize
  ): Option[PMetric] =
    if (labels.find(_._1.equals("le")).isDefined) None
    else
      Some(
        new PMetric(
          name,
          help,
          labels,
          Details.HistogramImpl(
            buckets.buckets(maxAge, maxSize),
            0,
            0
          )
        ) {}
      )

  def observeHistogram(m: PMetric, v: Double, t: java.time.Instant): Option[PMetric] =
    m.details match {
      case h: PMetric.Histogram => Some(new PMetric(m.name, m.help, m.labels, h.observe(v, t)) {})
      case _                    => None
    }

  // --------- Methods creating and using Prometheus Histograms

  def summary(
    name: String,
    help: String,
    labels: Chunk[Label],
    maxAge: java.time.Duration = TimeSeries.defaultMaxAge,
    maxSize: Int = TimeSeries.defaultMaxSize
  )(
    quantiles: Quantile*
  ): Option[PMetric] =
    if (labels.find(_._1.equals("quantile")).isDefined) None
    else
      Some(
        new PMetric(
          name,
          help,
          labels,
          Details.SummaryImpl(TimeSeries(maxAge, maxSize), Chunk.fromIterable(quantiles), 0, 0)
        ) {}
      )

  def observeSummary(m: PMetric, v: Double, t: java.time.Instant): Option[PMetric] =
    m.details match {
      case s: PMetric.Summary => Some(new PMetric(m.name, m.help, m.labels, s.observe(v, t)) {})
      case _                  => None
    }
}
