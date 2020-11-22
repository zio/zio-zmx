package zio.zmx.prometheus
import zio.Chunk

trait Metric {
  def name: String
  def help: Option[String]
  def labels: Map[String, String]
}

object Metric {
  sealed trait BucketType {
    def buckets: List[Double]
  }
  object BucketType       {

    type Buckets = Map[Double, Counter]

    // Count MUST exclude the +Inf bucket; i.e. bucket count 10 excludes the '11th +Inf bucket'
    final case class Linear(start: Double, width: Double, count: Int) extends BucketType {
      override def buckets: List[Double] = 0.until(count).map(i => start + i * width).toList
    }

    final case class Exponential(start: Double, factor: Double, count: Int) extends BucketType {
      override def buckets: List[Double] = 0.until(count).map(i => start * Math.pow(factor, i.toDouble)).toList
    }

  }

  final case class Quantile(
    phi: Double,  // The quantile
    error: Double // The error margin
  )

  final case class Counter(name: String, help: Option[String], labels: Map[String, String], count: Double)
      extends Metric {
    // Must haves
    def inc: Counter                    = copy(count = count + 1)
    def inc(d: Double): Option[Counter] = if (d >= 0) Some(copy(count = count + d)) else None

    // May haves
    def reset: Counter = copy(count = 0)

    // Encouraged
    // Dow we want to count exceptions / if so, how
  }

  // NOTE: keep smart constructor; instruct user to use it
  def counter(name: String, help: Option[String], labels: Map[String, String]) =
    Counter(name, help, labels, count = 0)

  final case class Gauge(name: String, help: Option[String], labels: Map[String, String], value: Double)
      extends Metric {
    // Must haves
    def inc: Gauge            = inc(1)
    def inc(v: Double): Gauge = copy(value = value + v)
    def dec: Gauge            = inc(-1)
    def dec(v: Double): Gauge = inc(-v)
    def set(v: Double): Gauge = copy(value = v)

    // Should haves
    def setToCurrentTime(): Gauge = copy(value = java.time.ZonedDateTime.now().toEpochSecond().toDouble)

    // Encouraged
    // start / stop timer
  }

  def gauge(name: String, help: Option[String], labels: Map[String, String])                  = Gauge(name, help, labels, value = 0)
  def gauge(name: String, help: Option[String], labels: Map[String, String], startAt: Double) =
    Gauge(name, help, labels, value = startAt)

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
  final case class Histogram(
    name: String,
    help: Option[String],
    labels: Map[String, String],
    buckets: BucketType.Buckets,
    sum: Double
  ) extends Metric {
    // MUST haves
    def observe(v: Double): Histogram = {

      // Find the largest bucket key where our observed value fits in
      val key: Double = buckets.keySet.fold(Double.MaxValue) { case (cur, k) =>
        if (v <= k && k <= cur) k else cur
      }
      copy(buckets = buckets.updated(key, buckets(key).inc), sum = sum + v)
    }

    def cnt = buckets.values.map(_.count).fold(0.0)(_ + _)

    // SHOULD haves
    // def time(seconds: Int): Double = ??? ==> Move to the interpreter of the model
  }
  // TODO: Remember to stick the infinite boundary bucket in here
  def histogram(name: String, help: Option[String], labels: Map[String, String], bucketType: BucketType) = {
    // TODO: given name, labels, and help in Counter we can no longer use a bucket of Counters as easily
    // using 'blank values' for now to be ignored during encoding
    val buckets = (bucketType.buckets ++ List(Double.MaxValue))
      .map(d => (d, Counter("", None, Map.empty[String, String], count = 0)))
      .toMap
    Histogram(name, help, labels, buckets, 0)
  }

  final case class Summary(
    name: String,
    help: Option[String],
    labels: Map[String, String],
    maxAge: Int,                     // Defines the sliding time window in seconds
    observed: Chunk[(Double, Long)], // The observed values with their timestamp
    quantiles: Seq[Metric.Quantile]  // The list of quantiles to be reported, may be empty
  ) extends Metric {
    // Must haves
    def observe(v: Double, now: java.time.Instant): Summary = {
      val millis = now.toEpochMilli()
      copy(observed = observed.dropWhile { case (_, t) => t < millis - maxAge * 1000L } ++ Chunk((v, millis)))
    }

    val sum = observed.foldLeft(0.0) { case (cur, (v, _)) => cur + v }
    val cnt = observed.length

    // Should haves
  }

  def summary(name: String, help: Option[String], labels: Map[String, String], maxAge: Int, quantile: Quantile*) =
    Summary(name, help, labels, maxAge = maxAge, observed = Chunk.empty, quantiles = quantile)

}
