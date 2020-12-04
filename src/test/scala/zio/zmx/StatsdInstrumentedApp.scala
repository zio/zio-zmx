package zio.zmx

import zio._

object StatsdInstrumentedApp extends App {

  import metrics._

  def doSomething    = ZMetrics.count("myCounter")(ZIO.succeed(()))
  def countSomething = ZIO.foreach_(1.to(100))(_ => doSomething)

  override def run(args: List[String]): URIO[ZEnv, ExitCode] = (for {
    _ <- countSomething.absorbWith(_ => new Exception("Boom")).orDie
  } yield ExitCode.success).provideCustomLayer(empty)

}
