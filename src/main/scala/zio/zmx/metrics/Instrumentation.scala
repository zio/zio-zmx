package zio.zmx.metrics

import zio._
import zio.clock.Clock
import MetricsDataModel._

trait Instrumentation[A] {
  def report: ZIO[Clock, Nothing, Option[A]] = ZIO.succeed(None)
  def handleMetric(me: TimedMetricEvent): ZIO[Any, Nothing, Unit]
}
