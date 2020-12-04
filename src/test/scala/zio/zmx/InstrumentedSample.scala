package zio.zmx

import zio._
import zio.zmx.metrics._

trait InstrumentedSample {

  def backend: ZLayer[Any, Nothing, ZMetrics]

  def doSomething    = ZMetrics.count("myCounter")(ZIO.succeed(()))
  def countSomething = ZIO.foreach_(1.to(100))(_ => doSomething)

  val program = (for {
    _ <- countSomething.absorbWith(_ => new Exception("Boom")).orDie
  } yield ExitCode.success).provideCustomLayer(backend)
}
