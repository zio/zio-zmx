package zio.zmx

import zio._

object EmptyInstrumentedApp extends App with InstrumentedSample {

  override def run(args: List[String]): URIO[ZEnv, ExitCode] = program(zio.zmx.metrics.empty)

}
