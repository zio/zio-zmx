package zio.zmx.metrics

import zio._

private[metrics] class EmptyInstrumentation extends ZMetrics.Service {

  override def increment(name: String): ZIO[Any, Nothing, Unit] =
    ZIO.succeed(())
}
