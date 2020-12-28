package zio.zmx.prometheus

import zio._
import zio.stm._
import zio.zmx.metrics.MetricsDataModel._

object PrometheusRegistry {

  def make(cfg: PrometheusConfig) = (for {
    items <- TMap.empty[String, PMetric]
  } yield new PrometheusRegistry(cfg, items)).commit
}

final class PrometheusRegistry private (
  cfg: PrometheusConfig,
  items: TMap[String, PMetric]
) {

  def update(me: TimedMetricEvent): ZIO[Any, Nothing, Unit] = ((for {
    z  <- zero(me.event)
    k   = me.metricKey
    om <- createOrGet(k, z)
    _  <- doUpdate(me, om)
  } yield ()).commit).catchAll(_ => ZIO.succeed(()))

  private def doUpdate(me: TimedMetricEvent, m: PMetric): ZSTM[Any, Nothing, Unit] = for {
    updated <- ZSTM.succeed {
                 me.event.details match {
                   case c: MetricEventDetails.Count =>
                     PMetric.incCounter(m, c.v)

                   case g: MetricEventDetails.GaugeChange =>
                     if (g.relative) PMetric.incGauge(m, g.v) else PMetric.setGauge(m, g.v)

                   case ov: MetricEventDetails.ObservedValue =>
                     PMetric.observeHistogram(m, ov.v)
                   case _                                    => None
                 }
               }
    _       <- ZSTM.foreach_(updated)(m => items.put(me.metricKey, m))
  } yield ()

  def list: ZIO[Any, Nothing, List[PMetric]] = items.values.commit

  // Create a zero element we can insert into the registry in case a metric with the given does not
  // exist
  private def zero(me: MetricEvent): ZSTM[Any, Option[Nothing], PMetric] = for {
    hs <- helpString(me)
    pm <- me.details match {
            case _: MetricEventDetails.Count         => ZSTM.succeed(PMetric.counter(me.name, hs, me.tags))
            case _: MetricEventDetails.GaugeChange   => ZSTM.succeed(PMetric.gauge(me.name, hs, me.tags))
            case _: MetricEventDetails.ObservedValue =>
              buckets(me).flatMap(b => ZSTM.fromOption(PMetric.histogram(me.name, hs, me.tags, b)))
            case _                                   => ZSTM.fail(None)
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

  // Get an existing metric if it exists with the proper type, otherwise fail
  private def createOrGet(
    key: String,
    zero: PMetric
  ): ZSTM[Any, Option[Nothing], PMetric] = for {
    pm <- items.getOrElse(key, zero)
    r  <- if (pm.details.getClass == zero.details.getClass) ZSTM.succeed(pm) else ZSTM.fail(None)
  } yield r

}
