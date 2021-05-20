package zio.zmx.statsd

import zio._
import zio.zmx.metrics.{ MetricKey, MetricListener }

class StatsdListener extends MetricListener {

  override def setGauge(key: MetricKey.Gauge, value: Double): UIO[Unit] = report(key, value)

  override def setCounter(key: MetricKey.Counter, value: Double): UIO[Unit] = report(key, value)

  override def observeHistogram(key: MetricKey.Histogram, value: Double): UIO[Unit] = report(key, value)

  override def observeSummary(key: MetricKey.Summary, value: Double): UIO[Unit] = report(key, value)

  private def report(key: MetricKey, v: Double) = ZIO.succeed(println(s"$key -- $v"))

}
