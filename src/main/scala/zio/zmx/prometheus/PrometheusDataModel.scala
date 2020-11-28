package zio.zmx.prometheus

import zio.Chunk

sealed abstract case class Metric[A <: Metric.Details](
  name: String,
  help: String,
  labels: Chunk[(String, String)],
  details: A
)

object Metric extends WithDoubleOrdering {

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
  def counter(name: String, help: String, labels: Chunk[(String, String)] = Chunk.empty): Metric[Counter] =
    new Metric[Counter](name, help, labels, Details.CounterImpl(0)) {}

  // The error case is a negative increment and is reflected by returning a null value
  def incCounter(c: Metric[Counter], v: Double = 1.0d): Option[Metric[Counter]]                           =
    if (v < 0) None else Some(new Metric[Counter](c.name, c.help, c.labels, c.details.inc(v)) {})

  // --------- Methods creating and using Prometheus Gauges

  def gauge(
    name: String,
    help: String,
    labels: Chunk[(String, String)] = Chunk.empty,
    startAt: Double = 0.0
  ): Metric[Gauge]                                                =
    new Metric[Gauge](name, help, labels, Details.GaugeImpl(startAt)) {}

  def incGauge(g: Metric[Gauge], v: Double = 1.0d): Metric[Gauge] =
    new Metric[Gauge](g.name, g.help, g.labels, g.details.inc(v)) {}

  def decGauge(g: Metric[Gauge], v: Double = 1.0d): Metric[Gauge] = incGauge(g, -v)

  // Set the value of the Gauge to the seconds corresponding to the given Instant
  def setToInstant(g: Metric[Gauge], t: java.time.Instant): Metric[Gauge] =
    new Metric[Gauge](g.name, g.help, g.labels, g.details.set((t.toEpochMilli / 1000L).toDouble)) {}

  // --------- Methods creating and using Prometheus Histograms

  def histogram(
    name: String,
    help: String,
    labels: Chunk[(String, String)] = Chunk.empty,
    buckets: BucketType,
    maxAge: java.time.Duration = TimeSeries.defaultMaxAge,
    maxSize: Int = TimeSeries.defaultMaxSize
  ): Option[Metric[Histogram]] =
    if (labels.find(_._1.equals("le")).isDefined) None
    else
      Some(
        new Metric[Histogram](
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

  def observeHistogram(h: Metric[Histogram], v: Double, t: java.time.Instant): Metric[Histogram] =
    new Metric[Histogram](h.name, h.help, h.labels, h.details.observe(v, t)) {}

  // --------- Methods creating and using Prometheus Histograms

  def summary(
    name: String,
    help: String,
    labels: Chunk[(String, String)],
    maxAge: java.time.Duration = TimeSeries.defaultMaxAge,
    maxSize: Int = TimeSeries.defaultMaxSize
  )(
    quantiles: Quantile*
  ): Option[Metric[Summary]] =
    if (labels.find(_._1.equals("quantile")).isDefined) None
    else
      Some(
        new Metric[Summary](
          name,
          help,
          labels,
          Details.SummaryImpl(TimeSeries(maxAge, maxSize), Chunk.fromIterable(quantiles), 0, 0)
        ) {}
      )

  def observeSummary(s: Metric[Summary], v: Double, t: java.time.Instant): Metric[Summary] =
    new Metric[Summary](s.name, s.help, s.labels, s.details.observe(v, t)) {}

}
