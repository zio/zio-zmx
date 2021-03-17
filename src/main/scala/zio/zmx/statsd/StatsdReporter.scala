package zio.zmx.statsd

import zio._

import zio.zmx.statsd.StatsdDataModel._
import zio.zmx.metrics._
import zio.zmx._

object StatsdInstrumentation {

  def make(config: StatsdConfig): ZIO[Any, Nothing, MetricsReporter] =
    for {
      gauges <- Ref.make[Map[String, Double]](Map.empty)
    } yield StatsdReporter(config, gauges)
}

final case class StatsdReporter(config: StatsdConfig, gauges: Ref[Map[String, Double]]) extends MetricsReporter {

  private val statsdClient = StatsdClient.live(config).orDie

  def report(event: MetricEvent) =
    event.details match {
      case c: MetricEventDetails.Count         => send(Metric.Counter(event.name, c.v, 1d, event.tags))
      case g: MetricEventDetails.GaugeChange   =>
        for {
          v <- updateGauge(event.metricKey)(v => if (g.relative) v + g.v else g.v)
          _ <- send(Metric.Gauge(event.name, v, event.tags))
        } yield ()
      case o: MetricEventDetails.ObservedValue => send(Metric.Histogram(event.name, o.v, 1.0d, event.tags))
      case k: MetricEventDetails.ObservedKey   => send(Metric.Set(event.name, k.key, event.tags))
      case _                                   => ZIO.unit
    }

  private def send(m: Metric[_]): ZIO[Any, Nothing, Unit] = (for {
    clt <- ZIO.service[StatsdClient.StatsdClientSvc]
    data = StatsdEncoder.encode(m)
    _   <- clt.write(Chunk.fromArray(data.getBytes()))
  } yield ()).provideLayer(statsdClient).catchAll(_ => ZIO.succeed(()))

  private def updateGauge(key: String)(f: Double => Double): ZIO[Any, Nothing, Double] =
    gauges.modify { map =>
      val value   = map.getOrElse(key, 0d)
      val updated = f(value)
      updated -> map.updated(key, updated)
    }
}
