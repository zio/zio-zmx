package zio.zmx.metrics

import zio.Chunk
import zio.zmx.Label

import java.time.Instant

final case class MetricEvent(
  name: String,
  details: MetricEventDetails,
  timestamp: Option[java.time.Instant],
  tags: Chunk[Label]
) { self =>

  def metricKey: String = {
    val labels = if (tags.isEmpty) "" else tags.map(t => s"${t._1}=${t._2}").mkString("{", ",", "}")
    s"$name$labels"
  }

  def withLabel(l: Label) =
    if (tags.find(_._1 == l._1).isDefined) self
    else copy(tags = MetricEvent.normaliseTags(tags ++ Chunk(l)))

  def at(ts: Instant) =
    copy(timestamp = Some(ts))
}

object MetricEvent {

  def apply(name: String, details: MetricEventDetails, timestamp: Instant, tags: Label*): MetricEvent =
    MetricEvent(name, details, Some(timestamp), normaliseTags(Chunk.fromIterable(tags)))

  def apply(name: String, details: MetricEventDetails, tags: Label*): MetricEvent =
    MetricEvent(name, details, None, normaliseTags(Chunk.fromIterable(tags)))

  private[metrics] def normaliseTags(tags: Chunk[Label]): Chunk[Label] =
    tags
      .foldLeft(Chunk.empty: Chunk[Label]) { case (c, l) =>
        if (c.find(e => e._1 == l._1).isDefined) c else c ++ Chunk(l)
      }
      .sortBy(_._1)
}
