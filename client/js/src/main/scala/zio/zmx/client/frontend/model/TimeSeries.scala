package zio.zmx.client.frontend.model

import zio._
import zio.metrics._

import zio.zmx.client.frontend.utils.DomUtils.Color
import zio.zmx.client.frontend.utils.Implicits._

import scala.scalajs.js
import zio.zmx.client.ClientMessage

final case class TimeSeriesKey(
  metric: MetricKey,
  subKey: Option[String] = None
) {
  val key: String = metric.longName + subKey.map(s => s" - $s").getOrElse("")
}

/**
 * A time series key uniquely identifies a single line within a diagram view.
 * For Counters and Gauges it can be directly derived from the MetricKey, for
 * the compound metrics it also requires a sub key to identify the bucket, the
 * quantile or the the set element.
 */
final case class TimeSeriesEntry private (
  key: TimeSeriesKey,
  when: js.Date,
  value: Double
)

object TimeSeriesEntry {

  def fromMetricsNotification(n: ClientMessage.MetricsNotification): Chunk[TimeSeriesEntry]        =
    Chunk.fromIterable(n.states.map { case (k, s) => fromMetricState(k, s, n.when.toJSDate) }).flatten

  private def fromMetricState(k: MetricKey, s: MetricState, when: js.Date): Chunk[TimeSeriesEntry] =
    (k, s.details) match {
      case (ck: MetricKey.Counter, MetricType.Counter(c)) =>
        Chunk(TimeSeriesEntry(TimeSeriesKey(ck), when, c))

      case (gk: MetricKey.Gauge, MetricType.Gauge(v)) =>
        Chunk(TimeSeriesEntry(TimeSeriesKey(gk), when, v))

      case (hk: MetricKey.Histogram, hist: MetricType.DoubleHistogram) =>
        val avg =
          if (hist.count > 0)
            Chunk(TimeSeriesEntry(TimeSeriesKey(hk, Some("avg")), when, hist.sum / hist.count))
          else
            Chunk.empty

        hist.buckets.map { case (le, v) =>
          TimeSeriesEntry(TimeSeriesKey(hk, Some(s"$le")), when, v.doubleValue())
        } ++ avg

      case (sk: MetricKey.Summary, summ: MetricType.Summary) =>
        val avg =
          if (summ.count > 0)
            Chunk(TimeSeriesEntry(TimeSeriesKey(sk, Some("avg")), when, summ.sum / summ.count))
          else
            Chunk.empty

        summ.quantiles.collect { case (q, Some(v)) =>
          TimeSeriesEntry(TimeSeriesKey(sk, Some(s"$q")), when, v)
        } ++ avg

      case (sk: MetricKey.SetCount, setCount: MetricType.SetCount) =>
        setCount.occurrences.map { case (t, c) =>
          TimeSeriesEntry(TimeSeriesKey(sk, Some(t)), when, c.doubleValue())
        }

      case _ => Chunk.empty
    }
}

/**
 * The configuration for a single time series graph within a diagram.
 * A single time series is either coming from a counter, a gauge or represents
 * a single element of a histogram, summary or set. For the latter a subkey
 * must be defined, which element is relevant for the time series.
 */

final case class TimeSeriesConfig(
  key: TimeSeriesKey,
  color: Color,
  tension: Double
)
