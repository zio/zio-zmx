package zio.zmx.metrics

import zio.Chunk
import java.time.Instant

private[zmx] object MetricsDataModel {

  final case class Label(
    key: String,
    value: String
  )

  object MetricEvent {
    val empty = MetricEvent("", Chunk.empty, "", MetricEventDetails.Empty)
  }

  final case class MetricEvent(
    name: String,
    tags: Chunk[Label],
    description: String,
    details: MetricEventDetails
  ) { self =>
    def describe(s: String) = copy(description = s)
    def withLabel(l: Label) = copy(tags = tags.filterNot(_.key == l.key) ++ Chunk(l))
    def at(ts: Instant)     = TimedMetricEvent(self, ts)
  }

  object TimedMetricEvent {
    val empty = TimedMetricEvent(MetricEvent.empty, Instant.ofEpochMilli(0L))
  }

  final case class TimedMetricEvent(
    event: MetricEvent,
    timestamp: java.time.Instant
  )

  sealed trait MetricEventDetails
  object MetricEventDetails {

    case object Empty extends MetricEventDetails

    // ZMX Counter support

    sealed abstract class Count(val v: Double) extends MetricEventDetails {
      override def toString() = s"Count($v)"
    }

    def count(v: Double): Option[Count] = if (v >= 0) Some(new Count(v) {}) else None

    // ZMX Gauge support

    sealed abstract class GaugeChange(val v: Double, relative: Boolean) extends MetricEventDetails {
      override def toString() = s"GaugeChange($v, ${if (relative) "relative" else "absolute"})"
    }

    def gaugeChange(v: Double, relative: Boolean): GaugeChange = new GaugeChange(v, relative) {}
  }

  def count(name: String, v: Double, tags: Label*): Option[MetricEvent] =
    MetricEventDetails.count(v).map(c => MetricEvent(name, normaliseTags(tags), "", c))

  def gauge(name: String, v: Double, tags: Label*): MetricEvent =
    MetricEvent(name, normaliseTags(tags), "", MetricEventDetails.gaugeChange(v, false))

  def gaugeChange(name: String, v: Double, tags: Label*): MetricEvent =
    MetricEvent(name, normaliseTags(tags), "", MetricEventDetails.gaugeChange(v, true))

  private def normaliseTags(tags: Seq[Label]): Chunk[Label] =
    tags
      .foldLeft(Chunk.empty: Chunk[Label]) { case (c, l) =>
        if (c.find(e => e.key == l.key).isDefined) c else c ++ Chunk(l)
      }
      .sortBy(_.key)
}
