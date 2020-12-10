package zio.zmx

import zio._
import zio.zmx.metrics._

object EmptyInstrumentedApp extends ZmxApp with InstrumentedSample {

  override def makeInstrumentation = ZIO.succeed(new EmptyInstrumentation())

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    program

}
