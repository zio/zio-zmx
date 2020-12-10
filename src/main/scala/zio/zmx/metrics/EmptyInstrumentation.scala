package zio.zmx.metrics

import zio._
import zio.zmx.metrics.MetricsDataModel._

private[zmx] final class EmptyInstrumentation extends Instrumentation {

  override def handleMetric(m: MetricEvent): ZIO[Any, Nothing, Unit] = ZIO.succeed(println(m.toString))
}
