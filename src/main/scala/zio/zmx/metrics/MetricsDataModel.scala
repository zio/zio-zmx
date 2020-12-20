package zio.zmx.metrics

import zio.Chunk
import java.time.Instant

private[zmx] object MetricsDataModel {

  type Label = (String, String)

  object MetricEvent {
    val empty = apply("", MetricEventDetails.Empty)

    def apply(name: String, details: MetricEventDetails, tags: (String, String)*) =
      new MetricEvent(name, normaliseTags(Chunk.fromIterable(tags)), details) {}

    private[metrics] def normaliseTags(tags: Chunk[Label]): Chunk[Label]          =
      tags
        .foldLeft(Chunk.empty: Chunk[Label]) { case (c, l) =>
          if (c.find(e => e._1 == l._1).isDefined) c else c ++ Chunk(l)
        }
        .sortBy(_._1)
  }

  abstract sealed class MetricEvent private (
    val name: String,
    val tags: Chunk[Label],
    val details: MetricEventDetails
  ) { self =>

    lazy val metricKey: String = {
      val labels = if (tags.isEmpty) "" else tags.map(t => s"${t._1}=${t._2}").mkString("{", ",", "}")
      s"$name$labels"
    }

    def withLabel(l: Label) =
      if (tags.find(_._1 == l._1).isDefined) self
      else new MetricEvent(name, MetricEvent.normaliseTags(tags ++ Chunk(l)), details) {}

    def at(ts: Instant)     = TimedMetricEvent(self, ts)
  }

  object TimedMetricEvent {
    val empty = TimedMetricEvent(MetricEvent.empty, Instant.ofEpochMilli(0L))
  }

  final case class TimedMetricEvent(
    event: MetricEvent,
    timestamp: java.time.Instant
  ) {
    def metricKey: String = event.metricKey
  }

  sealed trait MetricEventDetails
  object MetricEventDetails {

    case object Empty extends MetricEventDetails

    // ZMX Counter support

    sealed abstract class Count(val v: Double) extends MetricEventDetails {
      override def toString() = s"Count($v)"

      override def hashCode(): Int = v.hashCode()

      override def equals(that: Any): Boolean = that match {
        case c: Count => c.v == v
        case _        => false
      }
    }

    def count(v: Double): Option[Count] = if (v >= 0) Some(new Count(v) {}) else None

    // ZMX Gauge support

    sealed abstract class GaugeChange(val v: Double, val relative: Boolean) extends MetricEventDetails {
      override def toString() = s"GaugeChange($v, ${if (relative) "relative" else "absolute"})"

      override def hashCode(): Int = relative.hashCode() * 37 + v.hashCode()

      override def equals(that: Any): Boolean = that match {
        case gc: GaugeChange => gc.v == v && gc.relative == relative
        case _               => false
      }
    }

    def gaugeChange(v: Double, relative: Boolean): GaugeChange = new GaugeChange(v, relative) {}
  }

  def count(name: String, v: Double, tags: (String, String)*): Option[MetricEvent] =
    MetricEventDetails.count(v).map(c => MetricEvent(name, c, tags: _*))

  def gauge(name: String, v: Double, tags: (String, String)*): MetricEvent =
    MetricEvent(name, MetricEventDetails.gaugeChange(v, false), tags: _*)

  def gaugeChange(name: String, v: Double, tags: (String, String)*): MetricEvent =
    MetricEvent(name, MetricEventDetails.gaugeChange(v, true), tags: _*)

}
