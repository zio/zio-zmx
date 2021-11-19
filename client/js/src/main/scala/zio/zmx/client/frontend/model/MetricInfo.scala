package zio.zmx.client.frontend.model

import zio.metrics._
import zio.zmx.client.MetricsMessage
import zio.zmx.client.MetricsMessage._

/**
 * MetricSummaries are used within the overview tables to display the most important information
 * of the known metrics.
 */

final case class MetricInfo(
  metric: MetricKey,
  details: MetricInfoDetails
)

object MetricInfo {

  import MetricInfoDetails._

  def fromMessage(msg: MetricsMessage): Option[MetricInfo] = msg match {
    case GaugeChange(key, _, value, _)      => Some(MetricInfo(key, GaugeDetails(value)))
    case CounterChange(key, _, absValue, _) => Some(MetricInfo(key, CounterDetails(absValue)))
    case HistogramChange(key, _, value)     =>
      value.details match {
        case MetricType.DoubleHistogram(buckets, count, sum) =>
          Some(MetricInfo(key, HistogramDetails(buckets.size, count, sum)))
        case _                                               => None
      }
    case SummaryChange(key, _, value)       =>
      value.details match {
        case MetricType.Summary(error, quantiles, count, sum) =>
          Some(MetricInfo(key, SummaryDetails(quantiles.size, error, count, sum)))
        case _                                                => None
      }
    case SetChange(key, _, value)           =>
      value.details match {
        case MetricType.SetCount(setTag, occurrences) =>
          Some(MetricInfo(key, SetDetails(setTag, occurrences.size, occurrences.foldLeft(0L)(_ + _._2))))
        case _                                        => None
      }
  }
}

sealed trait MetricInfoDetails

object MetricInfoDetails {

  final case class CounterDetails private (
    current: Double
  ) extends MetricInfoDetails

  final case class GaugeDetails(
    current: Double
  ) extends MetricInfoDetails

  final case class HistogramDetails private (
    buckets: Int,
    count: Long,
    sum: Double
  ) extends MetricInfoDetails

  final case class SummaryDetails private (
    quantiles: Int,
    error: Double,
    count: Long,
    sum: Double
  ) extends MetricInfoDetails

  final case class SetDetails private (
    setTag: String,
    keys: Int,
    count: Long
  ) extends MetricInfoDetails
}
