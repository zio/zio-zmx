package zio.zmx.client.frontend.model

import zio._
import zio.metrics._

import org.scalajs.dom.ext.Color
import zio.zmx.client.frontend.utils.Implicits._
import java.time.Instant
import zio.zmx.client.MetricsMessage

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
  when: Instant,
  value: Double
)

object TimeSeriesEntry {
  // Make sure the TimeseriesKeys can only be created from within the companion object

  def fromMetricsMessage(msg: MetricsMessage): Chunk[TimeSeriesEntry] = msg match {
    case msg: MetricsMessage.CounterChange =>
      Chunk(TimeSeriesEntry(TimeSeriesKey(msg.key), msg.when, msg.absValue))

    case msg: MetricsMessage.GaugeChange =>
      Chunk(TimeSeriesEntry(TimeSeriesKey(msg.key), msg.when, msg.value))

    case msg: MetricsMessage.HistogramChange =>
      msg.value.details match {
        case hist: MetricType.DoubleHistogram =>
          val avg =
            if (hist.count > 0)
              Chunk(TimeSeriesEntry(TimeSeriesKey(msg.key, Some("avg")), msg.when, hist.sum / hist.count))
            else
              Chunk.empty

          hist.buckets.map { case (le, v) =>
            TimeSeriesEntry(TimeSeriesKey(msg.key, Some(s"$le")), msg.when, v.doubleValue())
          } ++ avg
        case _                                => Chunk.empty
      }

    case msg: MetricsMessage.SummaryChange =>
      msg.value.details match {
        case summ: MetricType.Summary =>
          val avg =
            if (summ.count > 0)
              Chunk(TimeSeriesEntry(TimeSeriesKey(msg.key, Some("avg")), msg.when, summ.sum / summ.count))
            else
              Chunk.empty

          summ.quantiles.collect { case (q, Some(v)) =>
            TimeSeriesEntry(TimeSeriesKey(msg.key, Some(s"$q")), msg.when, v)
          } ++ avg
        case _                        => Chunk.empty
      }

    case msg: MetricsMessage.SetChange =>
      msg.value.details match {
        case setCount: MetricType.SetCount =>
          setCount.occurrences.map { case (t, c) =>
            TimeSeriesEntry(TimeSeriesKey(msg.key, Some(t)), msg.when, c.doubleValue())
          }
        case _                             => Chunk.empty
      }
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
  tension: Double,
  maxSize: Int
)
