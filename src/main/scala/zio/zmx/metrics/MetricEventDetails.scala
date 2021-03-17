package zio.zmx.metrics

sealed trait MetricEventDetails

object MetricEventDetails {

  final case class Count(v: Double)                                    extends MetricEventDetails
  final case class GaugeChange(val v: Double, val relative: Boolean)   extends MetricEventDetails
  final case class ObservedValue(val v: Double, val ht: HistogramType) extends MetricEventDetails
  final case class ObservedKey(val key: String)                        extends MetricEventDetails

  def count(v: Double): Count =
    Count(v max 0)

  def gaugeChange(v: Double, relative: Boolean): GaugeChange =
    GaugeChange(v, relative)

  def observe(v: Double, ht: HistogramType): ObservedValue =
    ObservedValue(v, ht)

  def observe(key: String): ObservedKey =
    ObservedKey(key)
}
