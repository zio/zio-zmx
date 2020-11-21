package zio.zmx.prometheus
import zio.Chunk
import zio.zmx.prometheus.Metric.BucketType

sealed abstract case class Metric private (
  name: String,
  help: Option[String],
  labels: Map[String, String],
  metricType: MetricType
)

object Metric {
  sealed trait BucketType {
    def buckets: List[Double]
  }
  object BucketType       {

    type Buckets = Map[Double, MetricType.Counter]

    // Count MUST exclude the +Inf bucket; i.e. bucket count 10 excludes the '11th +Inf bucket'
    final case class Linear(start: Double, width: Double, count: Int) extends BucketType {
      override def buckets: List[Double] = 0.to(count).map(i => start + i * width).toList
    }

    final case class Exponential(start: Double, factor: Double, count: Int) extends BucketType {
      override def buckets: List[Double] = 0.to(count).map(i => start * Math.pow(factor, i.toDouble)).toList
    }
  }

  final case class Quantile(
    phi: Double,  // The quantile
    error: Double // The error margin
  )

  def counter(name: String, labels: Map[String, String]) = new Metric(name, None, labels, MetricType.Counter(count = 0)) {}

  def gauge(name: String, labels: Map[String, String])                  = new Metric(name, None, labels, MetricType.Gauge(value = 0)) {}
  def gauge(name: String, labels: Map[String, String], startAt: Double) =
    new Metric(name, None, labels, MetricType.Gauge(value = startAt)) {}

  // TODO: Remember to stick the infinite boundary bucket in here
  def histogram(name: String, labels: Map[String, String], bucketType: BucketType) = {
    val buckets = (bucketType.buckets ++ List(Double.MaxValue)).map(d => (d, MetricType.Counter(count = 0))).toMap
    new Metric(name, None, labels, MetricType.Histogram(buckets)) {}
  }

  def summary(name: String, labels: Map[String, String], maxAge: Int, quantile: Quantile*) =
    new Metric(name, None, labels, MetricType.Summary(maxAge = maxAge, observed = Chunk.empty, quantiles = quantile)) {}
}

sealed trait MetricType
object MetricType {
  final case class Counter(count: Double) extends MetricType {
    // Must haves
    def inc(): Counter                  = copy(count = count + 1)
    def inc(d: Double): Option[Counter] = if (d >= 0) Some(Counter(count = count + d)) else None

    // May haves
    def reset(): Counter = Counter(0)

    // Encouraged
    // Dow we want to count exceptions / if so, how
  }

  final case class Gauge(value: Double) extends MetricType {
    // Must haves
    def inc(): Gauge          = inc(1)
    def inc(v: Double): Gauge = copy(value = value + v)
    def dec(): Gauge          = inc(-1)
    def dev(v: Double): Gauge = inc(-v)
    def set(v: Double): Gauge = Gauge(v)

    // Should haves
    def setToCurrentTime(): Gauge = Gauge(java.time.ZonedDateTime.now().toEpochSecond().toDouble)

    // Encouraged
    // start / stop timer
  }

  /* Requirements:
   * A histogram MUST NOT allow le as a user-set label, as le is used internally to designate buckets.
   * A histogram MUST offer a way to manually choose the buckets. Ways to set buckets in a linear(start, width, count) and exponential(start, factor, count) fashion SHOULD be offered. Count MUST exclude the +Inf bucket.
   * A histogram SHOULD have the same default buckets as other client libraries. Buckets MUST NOT be changeable once the metric is created.
   * A histogram MUST have the following methods:
   *   - observe(double v): Observe the given amount
   * A histogram SHOULD have the following methods:
   * Some way to time code for users in seconds. In Python this is the time() decorator/context manager. In Java this is startTimer/observeDuration. Units other than seconds MUST NOT be offered (if a user wants something else, they can do it by hand). This should follow the same pattern as Gauge/Summary.
   * Histogram _count/_sum and the buckets MUST start at 0.
   */
  final case class Histogram(buckets: BucketType.Buckets) extends MetricType {
    // MUST haves
    def observe(v: Double) : Histogram= {

      // Find the largest bucket key where our observed value fits in
      val key : Double = buckets.view.keySet.fold(Double.MaxValue){ case (cur, k) => if (v <= k && k <= cur) k else cur }
      Histogram(buckets.updated(key, buckets(key).inc()))
    }

    // SHOULD haves
    // def time(seconds: Int): Double = ??? ==> Move to the interpreter of the model
  }

  final case class Summary(
    maxAge: Int,                     // Defines the sliding time window in seconds
    observed: Chunk[(Double, Long)], // The observed values with their timestamp
    quantiles: Seq[Metric.Quantile]  // The list of quantiles to be reported, may be empty
  ) extends MetricType {
    // Must haves
    def observe(v: Double, now: java.time.Instant): Summary = {
      val millis = now.toEpochMilli()
      copy(observed = observed.dropWhile { case (_, t) => t < millis - maxAge * 1000L } ++ Chunk((v, millis)))
    }

    val sum = observed.foldLeft(0.0) { case (cur, (v, _)) => cur + v }
    val cnt = observed.length

    // Should haves
  }
}
