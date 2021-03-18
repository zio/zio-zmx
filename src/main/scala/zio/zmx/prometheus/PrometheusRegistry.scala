package zio.zmx.prometheus

import zio._
import zio.clock._
import zio.zmx.metrics._
import zio.zmx.HistogramType
import java.time.Instant

object PrometheusRegistry {

  def make(config: PrometheusConfig): ZIO[Clock, Nothing, PrometheusRegistry] =
    for {
      metrics <- Ref.make[Map[String, PMetric]](Map.empty)
      clock   <- ZIO.service[Clock.Service]
    } yield PrometheusRegistry(config, metrics, clock)
}

final case class PrometheusRegistry private (
  cfg: PrometheusConfig,
  metrics: Ref[Map[String, PMetric]],
  clock: Clock.Service
) {

  def update(event: MetricEvent): ZIO[Any, Nothing, Unit] =
    for {
      instant <- clock.instant
      _       <- updateMetrics(event, instant)
    } yield ()

  private def updateMetrics(event: MetricEvent, now: Instant): ZIO[Any, Nothing, Unit] =
    metrics.update { map =>
      val maybeMetric = map.get(event.metricKey) match {
        case Some(metric) =>
          event.details match {
            case MetricEventDetails.Count(v) =>
              PMetric.incCounter(metric, v)

            case MetricEventDetails.GaugeChange(v, relative) =>
              if (relative) PMetric.incGauge(metric, v)
              else PMetric.setGauge(metric, v)

            case MetricEventDetails.ObservedValue(v, HistogramType.Histogram) =>
              PMetric.observeHistogram(metric, v)

            case MetricEventDetails.ObservedValue(v, HistogramType.Summary) =>
              PMetric.observeSummary(metric, v, event.timestamp.getOrElse(now))

            case MetricEventDetails.ObservedKey(_) => None
          }
        case None         => zero(event)
      }
      maybeMetric.fold(map)(v => map.updated(event.metricKey, v))
    }

  def list: ZIO[Any, Nothing, List[PMetric]] = metrics.get.map(_.values.toList)

  // Create a zero element we can insert into the registry in case a metric with the given does not
  // exist
  // Use a simple error channel to signal that we havent found a zero metric, so that
  // mapping the green path result is more convenient
  private def zero(me: MetricEvent): Option[PMetric] = {
    val hs = helpString(me)
    me.details match {
      // Create a counter starting from 0
      case _: MetricEventDetails.Count                                              =>
        Some(PMetric.counter(me.name, hs, me.tags))

      // Create a Gauge starting at zero
      case _: MetricEventDetails.GaugeChange                                        =>
        Some(PMetric.gauge(me.name, hs, me.tags))

      // Create a histogram
      case ov: MetricEventDetails.ObservedValue if ov.ht == HistogramType.Histogram =>
        PMetric.histogram(me.name, hs, me.tags, buckets(me))

      // Create a Summary
      case ov: MetricEventDetails.ObservedValue if ov.ht == HistogramType.Summary   =>
        PMetric.summary(me.name, hs, me.tags)(quantiles(me): _*)

      // Otherwise fail
      case _                                                                        => None
    }
  }

  private def helpString(me: MetricEvent): String =
    cfg.descriptions.find(d => d(me).isDefined).flatMap(d => d(me)).getOrElse("")

  private def buckets(me: MetricEvent): PMetric.Buckets =
    cfg.buckets.find(d => d(me).isDefined).flatMap(d => d(me)).getOrElse(PMetric.Buckets.Manual())

  private def quantiles(me: MetricEvent): Seq[Quantile] =
    cfg.quantiles.find(d => d(me).isDefined).flatMap(d => d(me)).getOrElse(PrometheusConfig.defaultQuantiles)
}
