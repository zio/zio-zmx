package zio.zmx.prometheus

import zio._
import zio.clock._
import zio.stm._
import zio.zmx.metrics._
import java.time.Instant

object PrometheusRegistry {

  def make(cfg: PrometheusConfig): ZIO[Clock, Nothing, PrometheusRegistry] =
    for {
      items <- TMap.empty[String, PMetric].commit
      clock <- ZIO.service[Clock.Service]
    } yield PrometheusRegistry(cfg, items, clock)
}

final case class PrometheusRegistry private (
  cfg: PrometheusConfig,
  items: TMap[String, PMetric],
  clock: Clock.Service
) {

  def update(me: MetricEvent): ZIO[Any, Nothing, Unit] =
    clock.instant.flatMap { now =>
      ((for {
        z  <- zero(me)
        k   = me.metricKey
        om <- createOrGet(k, z)
        _  <- doUpdate(me, om, now)
      } yield ()).commit).ignore
    }

  private def doUpdate(me: MetricEvent, m: PMetric, now: Instant): ZSTM[Any, Nothing, Unit] = for {
    updated <- ZSTM.succeed {
                 me.details match {
                   case c: MetricEventDetails.Count =>
                     PMetric.incCounter(m, c.v)

                   case g: MetricEventDetails.GaugeChange =>
                     if (g.relative) PMetric.incGauge(m, g.v) else PMetric.setGauge(m, g.v)

                   case ov: MetricEventDetails.ObservedValue if ov.ht == HistogramType.Histogram =>
                     PMetric.observeHistogram(m, ov.v)

                   case ov: MetricEventDetails.ObservedValue if ov.ht == HistogramType.Summary =>
                     PMetric.observeSummary(m, ov.v, me.timestamp.getOrElse(now))

                   case _ => None
                 }
               }
    _       <- ZSTM.foreach_(updated)(m => items.put(me.metricKey, m))
  } yield ()

  def list: ZIO[Any, Nothing, List[PMetric]] = items.values.commit

  // Create a zero element we can insert into the registry in case a metric with the given does not
  // exist
  // Use a simple error channel to signal that we havent found a zero metric, so that
  // mapping the green path result is more convenient
  private def zero(me: MetricEvent): ZSTM[Any, Option[Nothing], PMetric] = for {
    hs <- helpString(me)
    pm <- me.details match {
            // Create a counter starting from 0
            case _: MetricEventDetails.Count                                              =>
              ZSTM.succeed(PMetric.counter(me.name, hs, me.tags))

            // Create a Gauge starting at zero
            case _: MetricEventDetails.GaugeChange                                        =>
              ZSTM.succeed(PMetric.gauge(me.name, hs, me.tags))

            // Create a histogram
            case ov: MetricEventDetails.ObservedValue if ov.ht == HistogramType.Histogram =>
              buckets(me).flatMap(b => ZSTM.fromOption(PMetric.histogram(me.name, hs, me.tags, b)))

            // Create a Summary
            case ov: MetricEventDetails.ObservedValue if ov.ht == HistogramType.Summary   =>
              quantiles(me).flatMap(qs => ZSTM.fromOption(PMetric.summary(me.name, hs, me.tags)(qs: _*)))

            // Otherwise fail
            case _                                                                        => ZSTM.fail(None)
          }

  } yield pm

  private def helpString(me: MetricEvent): ZSTM[Any, Nothing, String] = for {
    cand <- ZSTM.succeed(cfg.descriptions.find(d => d(me).isDefined).flatMap(d => d(me)))
    res   = cand.getOrElse("")
  } yield res

  private def buckets(me: MetricEvent): ZSTM[Any, Nothing, PMetric.Buckets] = for {
    cand <- ZSTM.succeed(cfg.buckets.find(d => d(me).isDefined).flatMap(d => d(me)))
    res   = cand.getOrElse(PMetric.Buckets.Manual())
  } yield res

  private def quantiles(me: MetricEvent): ZSTM[Any, Nothing, Seq[Quantile]] = for {
    cand <- ZSTM.succeed(cfg.quantiles.find(d => d(me).isDefined).flatMap(d => d(me)))
    res   = cand.getOrElse(PrometheusConfig.defaultQuantiles)
  } yield res

  // Get an existing metric if it exists with the proper type, otherwise fail
  private def createOrGet(
    key: String,
    zero: PMetric
  ): ZSTM[Any, Option[Nothing], PMetric] = for {
    pm <- items.getOrElse(key, zero)
    r  <- if (pm.details.getClass == zero.details.getClass) ZSTM.succeed(pm) else ZSTM.fail(None)
  } yield r

}
