package zio.zmx

import zio._
import zio.console._
import zio.zmx.metrics._

object EmptyInstrumentedApp extends ZmxApp with InstrumentedSample {

  override def makeInstrumentation = ZIO.succeed(new EmptyInstrumentation())

  override def run(args: List[String]): URIO[ZEnv, ExitCode] = for {
    f <- program.fork
    _ <- getStrLn.catchAll(_ => ZIO.succeed(""))
    _ <- f.interrupt
  } yield (ExitCode.success)

}
