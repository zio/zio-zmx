package zio.zmx.metrics

import zio.Chunk
import java.time.Instant

private[zmx] object MetricsDataModel {

  type Label = (String, String)

  object MetricEvent {
    val empty = apply("", MetricEventDetails.Empty, Instant.ofEpochMilli(0L))

    def apply(name: String, details: MetricEventDetails, timestamp: Instant, tags: (String, String)*): MetricEvent =
      MetricEvent(name, details, Some(timestamp), normaliseTags(Chunk.fromIterable(tags)))

    def apply(name: String, details: MetricEventDetails, tags: (String, String)*): MetricEvent =
      MetricEvent(name, details, None, normaliseTags(Chunk.fromIterable(tags)))

    private[metrics] def normaliseTags(tags: Chunk[Label]): Chunk[Label] =
      tags
        .foldLeft(Chunk.empty: Chunk[Label]) { case (c, l) =>
          if (c.find(e => e._1 == l._1).isDefined) c else c ++ Chunk(l)
        }
        .sortBy(_._1)
  }

  final case class MetricEvent(
    name: String,
    details: MetricEventDetails,
    timestamp: Option[java.time.Instant],
    tags: Chunk[Label]
  ) { self =>

    lazy val metricKey: String = {
      val labels = if (tags.isEmpty) "" else tags.map(t => s"${t._1}=${t._2}").mkString("{", ",", "}")
      s"$name$labels"
    }

    def withLabel(l: Label) =
      if (tags.find(_._1 == l._1).isDefined) self
      else copy(tags = MetricEvent.normaliseTags(tags ++ Chunk(l)))

    def at(ts: Instant) =
      copy(timestamp = Some(ts))
  }

  sealed trait HistogramType
  object HistogramType {
    case object Histogram extends HistogramType {
      override def toString() = "Histogram"
    }

    case object Summary extends HistogramType {
      override def toString() = "Summary"
    }
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

    // Histogram support

    sealed abstract class ObservedValue(val v: Double, val ht: HistogramType) extends MetricEventDetails {
      override def toString() = s"ObservedValue($v, $ht)"

      override def hashCode(): Int = ht.toString().hashCode + 47 * v.hashCode()

      override def equals(that: Any): Boolean = that match {
        case ov: ObservedValue => ov.v == v && ov.ht.equals(ht)
        case _                 => false
      }
    }

    def observe(v: Double, ht: HistogramType): ObservedValue = new ObservedValue(v, ht) {}

    // Support to observe distinct names

    sealed abstract class ObservedKey(val key: String) extends MetricEventDetails {
      override def toString() = s"ObservedKey($key)"

      override def hashCode(): Int = key.hashCode() * 53

      override def equals(that: Any) = that match {
        case ok: ObservedKey => ok.key.equals(key)
        case _               => false
      }
    }

    def observe(key: String): ObservedKey = new ObservedKey(key) {}
  }

  def count(name: String, v: Double, tags: (String, String)*): Option[MetricEvent] =
    MetricEventDetails.count(v).map(c => MetricEvent(name, c, tags: _*))

  def gauge(name: String, v: Double, tags: (String, String)*): MetricEvent =
    MetricEvent(name, MetricEventDetails.gaugeChange(v, false), tags: _*)

  def gaugeChange(name: String, v: Double, tags: (String, String)*): MetricEvent =
    MetricEvent(name, MetricEventDetails.gaugeChange(v, true), tags: _*)

  def observe(name: String, v: Double, ht: HistogramType, tags: (String, String)*) =
    MetricEvent(name, MetricEventDetails.observe(v, ht), tags: _*)

  def observe(name: String, key: String, tags: (String, String)*) =
    MetricEvent(name, MetricEventDetails.observe(key), tags: _*)

}
