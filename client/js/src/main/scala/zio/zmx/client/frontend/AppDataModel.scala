package zio.zmx.client.frontend

import zio.Chunk

import zio.zmx.Label
import zio.zmx.client.MetricsMessage
import zio.zmx.client.MetricsMessage._

import zio.zmx.state.MetricType.DoubleHistogram
import zio.zmx.state.MetricType.Summary
import zio.zmx.state.MetricType.SetCount

object AppDataModel {

  sealed trait MetricSummary {
    def name: String
    def labels: String
    def longName: String = s"$name:$labels"
  }

  object MetricSummary {

    private def labels: Chunk[Label] => String = _.map { case (k, v) => s"$k=$v" }.mkString(",")

    def fromMessage(msg: MetricsMessage): Option[MetricSummary] = msg match {
      case GaugeChange(key, value, _)      => Some(GaugeInfo(key.name, labels(key.tags), value))
      case CounterChange(key, absValue, _) => Some(CounterInfo(key.name, labels(key.tags), absValue))
      case HistogramChange(key, value)     =>
        value.details match {
          case DoubleHistogram(buckets, count, sum) =>
            Some(HistogramInfo(key.name, labels(key.tags), buckets.size, count, sum))
          case _                                    => None
        }
      case SummaryChange(key, value)       =>
        value.details match {
          case Summary(error, quantiles, count, sum) =>
            Some(SummaryInfo(key.name, labels(key.tags), quantiles.size, error, count, sum))
          case _                                     => None
        }
      case SetChange(key, value)           =>
        value.details match {
          case SetCount(setTag, occurrences) =>
            Some(SetInfo(key.name, labels(key.tags), setTag, occurrences.size, occurrences.foldLeft(0L)(_ + _._2)))
          case _                             => None
        }
    }

    final case class CounterInfo(
      override val name: String,
      override val labels: String,
      current: Double
    ) extends MetricSummary

    final case class GaugeInfo(
      override val name: String,
      override val labels: String,
      current: Double
    ) extends MetricSummary

    final case class HistogramInfo(
      override val name: String,
      override val labels: String,
      buckets: Int,
      count: Long,
      sum: Double
    ) extends MetricSummary

    final case class SummaryInfo(
      override val name: String,
      override val labels: String,
      quantiles: Int,
      error: Double,
      count: Long,
      sum: Double
    ) extends MetricSummary

    final case class SetInfo(
      override val name: String,
      override val labels: String,
      setTag: String,
      keys: Int,
      count: Long
    ) extends MetricSummary
  }
}
