package zio.zmx.prometheus

import zio.Chunk

final case class PrometheusMetric(
  name: String,
  lables: Map[String, String],
  metricType: PrometheusMetricType
)

sealed trait PrometheusMetricType
object PrometheusMetricType {
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

  final case class Histogram() extends PrometheusMetricType {}

  final case class Quantile(
    phi: Double,  // The quantile
    error: Double // The error margin
  )

  final case class Summary(
    maxAge: Int,                      // Defines the sliding time window in seconds
    observed: Chunk[(Double, Long)],  // The observed values with their timestamp
    quantiles: List[Quantile]         // The list of quantiles to be reported, may be empty
  ) extends PrometheusMetricType {
    // Must haves
    def observe(v: Double): Summary = {
      // TODO: Revisit this
      val now = System.currentTimeMillis()
      copy(observed = observed.dropWhile{ case (_, t) => t < now - maxAge * 1000L } ++ Chunk((v, now)))
    }

    val sum = observed.foldLeft(0.0){ case (cur, (v, _)) => cur + v }
    val cnt = observed.length

    private def

    // Should haves
  }
}
