package zio.zmx

import zio._
import zio.console._
import zio.zmx.metrics._

object EmptyInstrumentedApp extends ZmxApp[Nothing] with InstrumentedSample {

  override def makeInstrumentation = ZIO.succeed(new EmptyInstrumentation())

  override def runInstrumented(args: List[String], inst: Instrumentation[Nothing]): URIO[ZEnv, ExitCode] = for {
    f <- program.fork
    _ <- getStrLn.catchAll(_ => ZIO.succeed(""))
    _ <- f.interrupt
  } yield (ExitCode.success)

}
