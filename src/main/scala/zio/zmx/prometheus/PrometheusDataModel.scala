package zio.zmx.prometheus
import zio.Chunk

final case class PrometheusMetric(
  name: String,
  lables: Map[String, String],
  metricType: PrometheusMetricType
)

sealed trait PrometheusMetricType
object PrometheusMetricType {
  sealed trait BucketType
  object BucketType {
    // Count MUST exclude the +Inf bucket; what does this mean?
    final case class Linear(start: Double, width: Double, count: Double)       extends BucketType
    final case class Exponential(start: Double, factor: Double, count: Double) extends BucketType
  }

  final case class Counter(count: Double) extends PrometheusMetricType {
    // Must haves
    def inc(): Counter                  = copy(count = count + 1)
    def inc(d: Double): Option[Counter] = if (d >= 0) Some(Counter(count = count + d)) else None

    // May haves
    def reset(): Counter = Counter(0)

    // Encouraged
    // Dow we want to count exceptions / if so, how
  }

  final case class Gauge(value: Double) extends PrometheusMetricType {
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
  sealed abstract case class Histogram private (le: ???, bucketType: BucketType) extends PrometheusMetricType {
    // MUST haves
    def observe(v: Double)

    // SHOULD haves
    def time(): Double
  }

  object Histogram {
    def fromInput(bucketType: BucketType): Histogram = new Histogram(le = ???, bucketType = bucketType) {} // apply?
  }

  final case class Quantile(
    phi: Double,  // The quantile
    error: Double // The error margin
  )

  final case class Summary(
    maxAge: Int,                     // Defines the sliding time window in seconds
    observed: Chunk[(Double, Long)], // The observed values with their timestamp
    quantiles: List[Quantile]        // The list of quantiles to be reported, may be empty
  ) extends PrometheusMetricType {
    // Must haves
    def observe(v: Double): Summary = {
      // TODO: Revisit this
      val now = System.currentTimeMillis()
      copy(observed = observed.dropWhile { case (_, t) => t < now - maxAge * 1000L } ++ Chunk((v, now)))
    }

    val sum = observed.foldLeft(0.0) { case (cur, (v, _)) => cur + v }
    val cnt = observed.length

    // Should haves
  }
}
