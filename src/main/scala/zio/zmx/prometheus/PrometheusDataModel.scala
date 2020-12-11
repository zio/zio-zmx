package zio.zmx.prometheus

import com.github.ghik.silencer.silent

import zio._
import zio.zmx.metrics.MetricsDataModel.Label

sealed abstract case class PMetric[+A <: PMetric.Details](
  name: String,
  help: String,
  labels: Chunk[Label],
  details: A
) {
  private val labelKey    = if (labels.isEmpty) "" else labels.map(p => s"${p.key}=${p.value}").mkString("{", ",", "}")
  val registryKey: String = s"$name$labelKey"
}

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

  @silent
  def create[A <: Details](name: String)(implicit tag: Tag[A]): PMetric[A] =
    tag match {
      case c: Tag[_] if c.closestClass == classOf[Counter] => counter(name, "", Chunk.empty).asInstanceOf[PMetric[A]]
    }

  // --------- Methods creating and using Prometheus counters
  def counter(name: String, help: String, labels: Chunk[Label] = Chunk.empty): PMetric[Counter] =
    new PMetric[Counter](name, help, labels, Details.CounterImpl(0)) {}

  // The error case is a negative increment and is reflected by returning a null value
  def incCounter(c: PMetric[Counter], v: Double = 1.0d): Option[PMetric[Counter]]               =
    if (v < 0) None else Some(new PMetric[Counter](c.name, c.help, c.labels, c.details.inc(v)) {})

  // --------- Methods creating and using Prometheus Gauges

  def gauge(
    name: String,
    help: String,
    labels: Chunk[Label] = Chunk.empty,
    startAt: Double = 0.0
  ): PMetric[Gauge]                                                 =
    new PMetric[Gauge](name, help, labels, Details.GaugeImpl(startAt)) {}

  def incGauge(g: PMetric[Gauge], v: Double = 1.0d): PMetric[Gauge] =
    new PMetric[Gauge](g.name, g.help, g.labels, g.details.inc(v)) {}

  def decGauge(g: PMetric[Gauge], v: Double = 1.0d): PMetric[Gauge] = incGauge(g, -v)

  // Set the value of the Gauge to the seconds corresponding to the given Instant
  def setToInstant(g: PMetric[Gauge], t: java.time.Instant): PMetric[Gauge] =
    new PMetric[Gauge](g.name, g.help, g.labels, g.details.set((t.toEpochMilli / 1000L).toDouble)) {}

  // --------- Methods creating and using Prometheus Histograms

  def histogram(
    name: String,
    help: String,
    labels: Chunk[Label] = Chunk.empty,
    buckets: BucketType,
    maxAge: java.time.Duration = TimeSeries.defaultMaxAge,
    maxSize: Int = TimeSeries.defaultMaxSize
  ): Option[PMetric[Histogram]] =
    if (labels.find(_.key.equals("le")).isDefined) None
    else
      Some(
        new PMetric[Histogram](
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

  def observeHistogram(h: PMetric[Histogram], v: Double, t: java.time.Instant): PMetric[Histogram] =
    new PMetric[Histogram](h.name, h.help, h.labels, h.details.observe(v, t)) {}

  // --------- Methods creating and using Prometheus Histograms

  def summary(
    name: String,
    help: String,
    labels: Chunk[Label],
    maxAge: java.time.Duration = TimeSeries.defaultMaxAge,
    maxSize: Int = TimeSeries.defaultMaxSize
  )(
    quantiles: Quantile*
  ): Option[PMetric[Summary]] =
    if (labels.find(_.key.equals("quantile")).isDefined) None
    else
      Some(
        new PMetric[Summary](
          name,
          help,
          labels,
          Details.SummaryImpl(TimeSeries(maxAge, maxSize), Chunk.fromIterable(quantiles), 0, 0)
        ) {}
      )

  def observeSummary(s: PMetric[Summary], v: Double, t: java.time.Instant): PMetric[Summary] =
    new PMetric[Summary](s.name, s.help, s.labels, s.details.observe(v, t)) {}

}
