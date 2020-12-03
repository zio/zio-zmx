package zio.zmx.metrics

import zio._
import zio.zmx.MetricsDataModel._

private[metrics] class EmptyInstrumentation extends ZMetrics.Service {

  override def counter(name: String): ZIO[Any, Nothing, Option[Metric.Counter]] =
    ZIO.succeed(Some(Metric.Counter(name, 0, 0d, Chunk.empty)))

  override def increment(m: Metric.Counter): ZIO[Any, Nothing, Option[Metric.Counter]] =
    ZIO.succeed(println(s"Incrementing ${m.name}")) *> ZIO.succeed(None)
}
