package zio.zmx.prometheus

import zio._
import zio.zmx.metrics.MetricsDataModel.Label

final case class PMetric(
  name: String,
  help: String,
  labels: Chunk[Label],
  details: PMetric.Details
) {
  override def toString(): String = {
    val lbls = if (labels.isEmpty) "" else labels.map(l => s"${l._1}->${l._2}").mkString("{", ",", "}")
    s"PMetric($name$lbls, $details)"
  }
}

object PMetric extends WithDoubleOrdering {

  sealed trait Details

  final case class Counter(count: Double) extends Details {
    def inc(v: Double): Counter = copy(count = count + v)
  }

  final case class Gauge(value: Double) extends Details {
    def inc(v: Double): Gauge = copy(value = value + v)
    def set(v: Double): Gauge = copy(value = v)
  }

  sealed trait Buckets {
    def boundaries: Chunk[Double]
    def buckets: Chunk[(Double, Double)] =
      boundaries.map(d => (d, 0d))
  }

  object Buckets {

    final case class Manual(limits: Double*) extends Buckets {
      override def boundaries: Chunk[Double] =
        Chunk.fromArray(limits.toArray.sorted(dblOrdering)) ++ Chunk(Double.MaxValue).distinct
    }

    final case class Linear(start: Double, width: Double, count: Int) extends Buckets {
      override def boundaries: Chunk[Double] =
        Chunk.fromArray(0.until(count).map(i => start + i * width).toArray) ++ Chunk(Double.MaxValue)
    }

    final case class Exponential(start: Double, factor: Double, count: Int) extends Buckets {
      override def boundaries: Chunk[Double] =
        Chunk.fromArray(0.until(count).map(i => start * Math.pow(factor, i.toDouble)).toArray) ++ Chunk(Double.MaxValue)
    }
  }

  final case class Histogram(
    buckets: Chunk[(Double, Double)],
    count: Double,
    sum: Double
  ) extends Details { self =>
    def observe(v: Double): Histogram = copy(
      buckets = self.buckets.map(b => if (v <= b._1) (b._1, b._2 + 1d) else b),
      count = self.count + 1,
      sum = self.sum + v
    )
  }

  final case class Summary(
    samples: TimeSeries,
    quantiles: Chunk[Quantile],
    count: Double,
    sum: Double
  ) extends Details { self =>
    def observe(v: Double, t: java.time.Instant): Summary = copy(
      count = self.count + 1,
      sum = self.sum + v,
      samples = self.samples.observe(v, t)
    )
  }

  // --------- Methods creating and using Prometheus counters
  def counter(name: String, help: String, labels: Chunk[Label] = Chunk.empty): PMetric =
    PMetric(name, help, labels, Counter(0))

  // The error case is a negative increment and is reflected by returning a None
  def incCounter(m: PMetric, v: Double = 1.0d): Option[PMetric] =
    m.details match {
      case c: PMetric.Counter =>
        if (v < 0) None else Some(PMetric(m.name, m.help, m.labels, c.inc(v)))
      case _                  => None
    }

  // --------- Methods creating and using Prometheus Gauges

  def gauge(
    name: String,
    help: String,
    labels: Chunk[Label] = Chunk.empty,
    startAt: Double = 0.0
  ): PMetric =
    PMetric(name, help, labels, Gauge(startAt))

  def incGauge(m: PMetric, v: Double = 1.0d): Option[PMetric] =
    m.details match {
      case g: PMetric.Gauge => Some(PMetric(m.name, m.help, m.labels, g.inc(v)))
      case _                => None
    }

  def decGauge(m: PMetric, v: Double = 1.0d): Option[PMetric] = incGauge(m, -v)

  def setGauge(m: PMetric, v: Double): Option[PMetric] =
    m.details match {
      case _: PMetric.Gauge => Some(PMetric(m.name, m.help, m.labels, Gauge(v)))
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
    buckets: Buckets
  ): Option[PMetric] =
    if (labels.find(_._1.equals("le")).isDefined) None
    else
      Some(
        PMetric(
          name,
          help,
          labels,
          Histogram(buckets.buckets, 0, 0)
        )
      )

  def observeHistogram(m: PMetric, v: Double): Option[PMetric] =
    m.details match {
      case h: Histogram =>
        val updated = PMetric(m.name, m.help, m.labels, h.observe(v))
        Some(updated)
      case _            => None
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
        PMetric(
          name,
          help,
          labels,
          Summary(TimeSeries(maxAge, maxSize), Chunk.fromIterable(quantiles), 0, 0)
        )
      )

  def observeSummary(m: PMetric, v: Double, t: java.time.Instant): Option[PMetric] =
    m.details match {
      case s: PMetric.Summary => Some(PMetric(m.name, m.help, m.labels, s.observe(v, t)))
      case _                  => None
    }
}
