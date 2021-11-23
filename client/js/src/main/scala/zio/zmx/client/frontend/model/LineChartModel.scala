package zio.zmx.client.frontend.model

import zio.Chunk

trait LineChartModel {

  // Get the data currently available
  def snapshot: Map[TimeSeriesKey, Chunk[TimeSeriesEntry]]

  def recordEntry(entry: TimeSeriesEntry): LineChartModel

  def updateMaxSamples(newMax: Int): LineChartModel
}

object LineChartModel {

  def apply(maxSamples: Int) = LineChartModelImpl(maxSamples, Map.empty)

  final case class LineChartModelImpl private (
    maxSamples: Int,
    content: Map[TimeSeriesKey, Chunk[TimeSeriesEntry]]
  ) extends LineChartModel { self =>

    def snapshot: Map[TimeSeriesKey, Chunk[TimeSeriesEntry]] = content

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
