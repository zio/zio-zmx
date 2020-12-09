package zio.zmx

import zio._
import zio.zmx.metrics._

trait InstrumentedSample {

  def doSomething    = ZMetrics.count("myCounter")(ZIO.succeed(print(".")))
  def doSomething2   = ZIO.succeed(print(".")).counted("myCounter2")
  def countSomething = ZIO.foreach_(1.to(100))(_ => doSomething2.zip(doSomething))

  def program: ZIO[ZEnv with ZMetrics, Nothing, ExitCode] = for {
    _ <- countSomething.absorbWith(_ => new Exception("Boom")).orDie
  } yield ExitCode.success
}
