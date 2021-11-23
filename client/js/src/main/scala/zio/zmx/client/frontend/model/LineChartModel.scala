package zio.zmx.client.frontend.model

import zio.Chunk
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

trait LineChartModel {

  // Get the data currently available
  def snapshot: js.Array[js.Object with js.Dynamic]

  def recordEntry(entry: TimeSeriesEntry): LineChartModel

  def updateMaxSamples(newMax: Int): LineChartModel
}

object LineChartModel {

  def apply(maxSamples: Int) = LineChartModelImpl(maxSamples, Map.empty)

  final case class LineChartModelImpl private (
    maxSamples: Int,
    content: Map[TimeSeriesKey, Chunk[TimeSeriesEntry]]
  ) extends LineChartModel { self =>

    def snapshot: js.Array[js.Object with js.Dynamic] = {

      val res = content.values
        .map(_.toSeq)
        .flatten
        .map { entry =>
          js.Dynamic.literal(
            "key"   -> entry.key.key,
            "date"  -> new js.Date(entry.when.toEpochMilli().toDouble),
            "value" -> entry.value
          )
        }
        .toJSArray

      println(js.JSON.stringify(res))

      res
    }

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
