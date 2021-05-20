package zio.zmx.statsd

import zio._
import zio.zmx.metrics.{ MetricKey, MetricListener }

object StatsdListener {

  def make =
    ZIO.service[StatsdClient.StatsdClientSvc].map(new StatsdListener(_) {})

}

sealed abstract class StatsdListener(client: StatsdClient.StatsdClientSvc) extends MetricListener {

  override def setGauge(key: MetricKey.Gauge, value: Double): UIO[Unit] = report(key, value)

  override def setCounter(key: MetricKey.Counter, value: Double): UIO[Unit] = report(key, value)

  override def observeHistogram(key: MetricKey.Histogram, value: Double): UIO[Unit] = report(key, value)

  override def observeSummary(key: MetricKey.Summary, value: Double): UIO[Unit] = report(key, value)

  private def report(key: MetricKey, v: Double) =
    StatsdEncoder
      .encode(key, v)
      .foldM(
        _ => ZIO.unit,
        s =>
          (client.write(s).flatMap(l => ZIO.succeed(println(s"Wrote [$l] bytes")))).catchAll(t => ZIO.succeed(t.printStackTrace()))
      )

}
