package zio.zmx.metrics

import zio._
import zio.clock.Clock
import MetricsDataModel._

trait Instrumentation {
  def report: ZIO[Clock, Nothing, Option[String]]     = ZIO.succeed(None)
  def reportJson: ZIO[Clock, Nothing, Option[String]] = ZIO.succeed(None)
  def handleMetric(me: TimedMetricEvent): ZIO[Any, Nothing, Unit]
}
