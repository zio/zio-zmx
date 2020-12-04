package zio.zmx

import zio._

object EmptyInstrumentedApp extends App with InstrumentedSample {

  override val backend = zio.zmx.metrics.empty

  override def run(args: List[String]): URIO[ZEnv, ExitCode] = program

}
