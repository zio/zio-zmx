package zio.zmx.client.frontend.model

import scala.scalajs.js

import zio._
import zio.metrics._
import zio.zmx.client.ClientMessage
import zio.zmx.client.frontend.utils.DomUtils.Color
import zio.zmx.client.frontend.utils.Implicits._

final case class TimeSeriesKey(
  metric: MetricKey.Untyped,
  subKey: Option[String] = None) {
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
  value: Double)

object TimeSeriesEntry {

  def fromMetricsNotification(n: ClientMessage.MetricsNotification): Chunk[TimeSeriesEntry] =
    Chunk.fromIterable(n.states.map(pair => fromMetricState(pair, n.when.toJSDate))).flatten

  // We need to produce a Chunk of TimeSeries entries as many metrics may produce multiple lines
  private def fromMetricState(pair: MetricPair.Untyped, when: js.Date): Chunk[TimeSeriesEntry] =
    pair.metricState match {
      case s: MetricState.Counter =>
        Chunk(TimeSeriesEntry(TimeSeriesKey(pair.metricKey), when, s.count))

      case s: MetricState.Gauge =>
        Chunk(TimeSeriesEntry(TimeSeriesKey(pair.metricKey), when, s.value))

      // Each bucket and also the calculated average will produce its own timeseries
      case s: MetricState.Histogram =>
        val avg =
          if (s.count > 0)
            Chunk(TimeSeriesEntry(TimeSeriesKey(pair.metricKey, Some("avg")), when, s.sum / s.count))
          else
            Chunk.empty

        s.buckets.map { case (le, v) =>
          TimeSeriesEntry(TimeSeriesKey(pair.metricKey, Some(s"$le")), when, v.doubleValue())
        } ++ avg

      case s: MetricState.Summary =>
        val avg =
          if (s.count > 0)
            Chunk(TimeSeriesEntry(TimeSeriesKey(pair.metricKey, Some("avg")), when, s.sum / s.count))
          else
            Chunk.empty

        s.quantiles.collect { case (q, Some(v)) =>
          TimeSeriesEntry(TimeSeriesKey(pair.metricKey, Some(s"$q")), when, v)
        } ++ avg

      case s: MetricState.Frequency =>
        Chunk.fromIterable(s.occurrences.map { case (t, c) =>
          TimeSeriesEntry(TimeSeriesKey(pair.metricKey, Some(t)), when, c.doubleValue())
        })

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
  tension: Double)
