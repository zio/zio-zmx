package zio.zmx.client.frontend.model

import zio._
import zio.metrics._

import zio.zmx.client.frontend.utils.DomUtils.Color
import zio.zmx.client.frontend.utils.Implicits._

import scala.scalajs.js
import zio.zmx.client.ClientMessage

final case class TimeSeriesKey(
  metric: MetricKey.Untyped,
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

  def fromMetricsNotification(n: ClientMessage.MetricsNotification): Chunk[TimeSeriesEntry] =
    Chunk.fromIterable(n.states.map(pair => fromMetricState(pair, n.when.toJSDate))).flatten

  // We need to produce a Chunk of TimeSeries entries as many metrics may produce multiple lines
  private def fromMetricState(pair: MetricPair.Untyped, when: js.Date): Chunk[TimeSeriesEntry] =
    pair.metricKey match {
      case kc if kc.isInstanceOf[MetricKey.Counter] =>
        Chunk(TimeSeriesEntry(TimeSeriesKey(kc), when, pair.metricState.asInstanceOf[MetricState.Counter].count))

      case kg if kg.isInstanceOf[MetricKey.Gauge]     =>
        Chunk(TimeSeriesEntry(TimeSeriesKey(kg), when, pair.metricState.asInstanceOf[MetricState.Gauge].value))

      // Each bucket and also the calculated average will produce its own timeseries
      case kh if kh.isInstanceOf[MetricKey.Histogram] =>
        val hist = pair.metricState.asInstanceOf[MetricState.Histogram]
        val avg  =
          if (hist.count > 0)
            Chunk(TimeSeriesEntry(TimeSeriesKey(kh, Some("avg")), when, hist.sum / hist.count))
          else
            Chunk.empty

        hist.buckets.map { case (le, v) =>
          TimeSeriesEntry(TimeSeriesKey(kh, Some(s"$le")), when, v.doubleValue())
        } ++ avg

      case ks if ks.isInstanceOf[MetricKey.Summary] =>
        val summ = pair.metricState.asInstanceOf[MetricState.Summary]
        val avg  =
          if (summ.count > 0)
            Chunk(TimeSeriesEntry(TimeSeriesKey(ks, Some("avg")), when, summ.sum / summ.count))
          else
            Chunk.empty

        summ.quantiles.collect { case (q, Some(v)) =>
          TimeSeriesEntry(TimeSeriesKey(ks, Some(s"$q")), when, v)
        } ++ avg

      case kf if kf.isInstanceOf[MetricKey.Frequency] =>
        val freq = pair.metricState.asInstanceOf[MetricState.Frequency]
        Chunk.fromIterable(freq.occurrences.map { case (t, c) =>
          TimeSeriesEntry(TimeSeriesKey(kf, Some(t)), when, c.doubleValue())
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
  tension: Double
)
