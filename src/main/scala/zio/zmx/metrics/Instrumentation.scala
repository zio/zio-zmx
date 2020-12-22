package zio.zmx.metrics

import zio._
import zio.clock.Clock
import MetricsDataModel._

trait Instrumentation {
  def report: ZIO[Clock, Nothing, String] = ZIO.succeed("")
  def handleMetric(me: TimedMetricEvent): ZIO[Any, Nothing, Unit]
}
