package zio.zmx

import zio._
import zio.zmx.metrics._

trait InstrumentedSample {

  def doSomething    = ZMetrics.count("myCounter")(ZIO.succeed(print(".")))
  def countSomething = ZIO.foreach_(1.to(100))(_ => doSomething)

  def program: ZIO[ZEnv with ZMetrics, Nothing, ExitCode] = for {
    _ <- countSomething.absorbWith(_ => new Exception("Boom")).orDie
  } yield ExitCode.success
}
