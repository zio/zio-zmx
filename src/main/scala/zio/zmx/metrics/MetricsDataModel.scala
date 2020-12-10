package zio.zmx.metrics

import zio.Chunk

object MetricsDataModel {

  final case class Label(
    key: String,
    value: String
  )

  final case class MetricEvent(
    name: String,
    description: String,
    tags: Chunk[Label],
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

  def count(name: String, v: Double = 1d): Option[MetricEvent] =
    MetricEventDetails.count(v).map(c => MetricEvent(name, "", Chunk.empty, c))
}
