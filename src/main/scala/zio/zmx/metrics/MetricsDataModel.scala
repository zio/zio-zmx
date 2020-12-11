package zio.zmx.metrics

import java.time.Instant
import zio.Chunk

object MetricsDataModel {

  final case class Label(
    key: String,
    value: String
  )

  final case class MetricEvent(
    name: String,
    tags: Chunk[Label],
    timestamp: Instant,
    description: String,
    details: MetricEventDetails
  ) {
    def describe(s: String) = copy(description = s)
    def withLabel(l: Label) = copy(tags = tags.filterNot(_.key == l.key) ++ Chunk(l))
  }

  sealed trait MetricEventDetails
  object MetricEventDetails {

    sealed abstract class Count(val v: Double) extends MetricEventDetails {
      override def toString() = s"Count($v)"
    }

    def count(v: Double): Option[Count] = if (v >= 0) Option(new Count(v) {}) else None
  }

  def count(name: String, ts: Instant, tags: Label*): Option[MetricEvent] =
    count(name, 1d, ts, tags: _*)

  def count(name: String, v: Double, ts: Instant, tags: Label*): Option[MetricEvent] =
    MetricEventDetails.count(v).map(c => MetricEvent(name, normaliseTags(tags), ts, "", c))

  private def normaliseTags(tags: Seq[Label]): Chunk[Label] =
    tags
      .foldLeft(Chunk.empty[Label]) { case (c, l) => if (c.find(e => e.key == l.key).isDefined) c else c ++ Chunk(l) }
      .sortBy(_.key)
}
