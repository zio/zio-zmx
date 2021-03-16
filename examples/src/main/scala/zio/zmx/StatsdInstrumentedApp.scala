package zio.zmx

import zio._
import zio.console._

object StatsdInstrumentedApp extends StatsdApp with InstrumentedSample {

  override def runInstrumented(args: List[String]): URIO[ZEnv, ExitCode] = for {
    f <- program.fork
    _ <- getStrLn.catchAll(_ => ZIO.succeed(""))
    _ <- f.interrupt
  } yield (ExitCode.success)

}
