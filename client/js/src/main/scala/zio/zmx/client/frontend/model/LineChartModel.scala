package zio.zmx.client.frontend.model

import scala.scalajs.js

import zio.Chunk
import zio.metrics.MetricKey

trait LineChartModel {

  // Get the data currently available
  def minTime: js.Date
  def maxTime: js.Date

  def minValue: Double
  def maxValue: Double

  def data: Map[TimeSeriesKey, Chunk[TimeSeriesEntry]]

  def removeMetric(k: MetricKey.Untyped): LineChartModel

  def recordEntry(entry: TimeSeriesEntry): LineChartModel

  def updateMaxSamples(newMax: Int): LineChartModel
}

object LineChartModel {

  def apply(maxSamples: Int): LineChartModel = LineChartModelImpl(maxSamples, Map.empty)

  final case class LineChartModelImpl private (
    maxSamples: Int,
    content: Map[TimeSeriesKey, Chunk[TimeSeriesEntry]] = Map.empty)
      extends LineChartModel { self =>

    override def toString(): String = {
      val summary = content.map { case (k, c) => (k.key, c.size) }
      s"LineChartModel($summary)"
    }

    private val defaultDate: js.Date                     = new js.Date(0d)
    implicit private val dateOrdering: Ordering[js.Date] = new Ordering[js.Date] {

      def compare(x: js.Date, y: js.Date): Int =
        if (x.getTime() < y.getTime()) -1 else if (y.getTime() > x.getTime()) 1 else 0
    }

    def minTime: js.Date = content match {
      case e if e.isEmpty => defaultDate
      case m              =>
        m.values.map {
          _ match {
            // case Chunk.empty => defaultDate
            case Chunk() => defaultDate
            case c       => c.map(_.when).min
          }
        }.min
    }

    def maxTime: js.Date = content match {
      case e if e.isEmpty => defaultDate
      case m              =>
        m.values.map {
          _ match {
            case Chunk() => defaultDate
            case c       => c.map(_.when).max
          }
        }.max
    }

    def minValue: Double = content match {
      case e if e.isEmpty => Double.MaxValue
      case m              =>
        m.values.map {
          case Chunk() => Double.MaxValue
          case c       => c.map(_.value).min
        }.min
    }

    def maxValue: Double = content match {
      case e if e.isEmpty => Double.MinValue
      case m              =>
        m.values.map {
          case Chunk() => Double.MinValue
          case c       => c.map(_.value).max
        }.max
    }

    def removeMetric(metric: MetricKey.Untyped): LineChartModel =
      copy(content = content.filter { case (k, _) => !k.metric.equals(metric) })

    def data: Map[TimeSeriesKey, Chunk[TimeSeriesEntry]] = content

    def recordEntry(entry: TimeSeriesEntry): LineChartModel = {
      val updContent = content.get(entry.key) match {
        case None    => content.updated(entry.key, Chunk(entry))
        case Some(c) => content.updated(entry.key, (c :+ entry).takeRight(maxSamples))
      }
      self.copy(content = updContent)
    }

    def updateMaxSamples(newMax: Int): LineChartModel = self.copy(maxSamples = newMax)
  }

}
