package zio.zmx.client.frontend.model

import zio.metrics._
import zio.zmx.client.MetricsMessage
import zio.zmx.client.MetricsMessage._

/**
 * MetricSummaries are used within the overview tables to display the most important information
 * of the known metrics.
 */
sealed trait MetricSummary {
  def metric: MetricKey
}

object MetricSummary {

  def fromMessage(msg: MetricsMessage): Option[MetricSummary] = msg match {
    case GaugeChange(key, _, value, _)      => Some(GaugeInfo(key, value))
    case CounterChange(key, _, absValue, _) => Some(CounterInfo(key, absValue))
    case HistogramChange(key, _, value)     =>
      value.details match {
        case MetricType.DoubleHistogram(buckets, count, sum) =>
          Some(HistogramInfo(key, buckets.size, count, sum))
        case _                                               => None
      }
    case SummaryChange(key, _, value)       =>
      value.details match {
        case MetricType.Summary(error, quantiles, count, sum) =>
          Some(SummaryInfo(key, quantiles.size, error, count, sum))
        case _                                                => None
      }
    case SetChange(key, _, value)           =>
      value.details match {
        case MetricType.SetCount(setTag, occurrences) =>
          Some(SetInfo(key, setTag, occurrences.size, occurrences.foldLeft(0L)(_ + _._2)))
        case _                                        => None
      }
  }

  final case class CounterInfo(
    override val metric: MetricKey,
    current: Double
  ) extends MetricSummary

  final case class GaugeInfo(
    override val metric: MetricKey,
    current: Double
  ) extends MetricSummary

  final case class HistogramInfo(
    override val metric: MetricKey,
    buckets: Int,
    count: Long,
    sum: Double
  ) extends MetricSummary

  final case class SummaryInfo(
    override val metric: MetricKey,
    quantiles: Int,
    error: Double,
    count: Long,
    sum: Double
  ) extends MetricSummary

  final case class SetInfo(
    override val metric: MetricKey,
    setTag: String,
    keys: Int,
    count: Long
  ) extends MetricSummary
}
