package zio.zmx.metrics

import zio._

private[zmx] final class EmptyInstrumentation extends Instrumentation {

  override def handleMetric = { case m =>
    ZIO.succeed(println(m.toString))
  }
}
