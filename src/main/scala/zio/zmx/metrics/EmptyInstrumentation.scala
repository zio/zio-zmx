package zio.zmx.metrics

import zio._
import MetricsDataModel.TimedMetricEvent

private[zmx] final class EmptyInstrumentation extends Instrumentation[Nothing] {

  override def handleMetric(me: TimedMetricEvent) = ZIO.succeed(println(me.toString))
}
