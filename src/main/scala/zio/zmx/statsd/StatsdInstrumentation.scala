package zio.zmx.statsd

import zio._

import zio.zmx.statsd.StatsdDataModel._
import zio.zmx.metrics.MetricsDataModel._
import zio.zmx.metrics.Instrumentation
import zio.stm._

object StatsdInstrumentation {

  def make(cfg: StatsdConfig): ZIO[Any, Nothing, Instrumentation] =
    TMap.empty[String, Double].commit.map(g => new StatsdInstrumentation(cfg, g) {})
}

sealed abstract class StatsdInstrumentation(config: StatsdConfig, gauges: TMap[String, Double])
    extends Instrumentation {

  private val statsdClient = StatsdClient.live(config).orDie

  def handleMetric(me: TimedMetricEvent) =
    me.event.details match {
      case c: MetricEventDetails.Count         => send(Metric.Counter(me.event.name, c.v, 1d, me.event.tags))
      case g: MetricEventDetails.GaugeChange   =>
        for {
          v <- updateGauge(me.metricKey)(v => if (g.relative) v + g.v else g.v)
          _ <- send(Metric.Gauge(me.event.name, v, me.event.tags))
        } yield ()
      case o: MetricEventDetails.ObservedValue => send(Metric.Histogram(me.event.name, o.v, 1.0d, me.event.tags))
      case k: MetricEventDetails.ObservedKey   => send(Metric.Set(me.event.name, k.key, me.event.tags))
      case _                                   => ZIO.unit
    }

  private def send(m: Metric[_]): ZIO[Any, Nothing, Unit] = (for {
    clt <- ZIO.service[StatsdClient.StatsdClientSvc]
    data = StatsdEncoder.encode(m)
    _   <- clt.write(Chunk.fromArray(data.getBytes()))
  } yield ()).provideLayer(statsdClient).catchAll(_ => ZIO.succeed(()))

  private def updateGauge(key: String)(f: Double => Double): ZIO[Any, Nothing, Double] =
    gauges.getOrElse(key, 0d).flatMap(v => gauges.put(key, f(v)) >>> ZSTM.succeed(v)).commit

}
