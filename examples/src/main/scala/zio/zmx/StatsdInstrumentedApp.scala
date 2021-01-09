package zio.zmx

import zio._
import zio.console._
import zio.zmx.metrics._
import zio.zmx.statsd._

object StatsdInstrumentedApp extends ZmxApp[Nothing] with InstrumentedSample {

  private val config = StatsdConfig(
    host = "localhost",
    port = 8125
  )

  override def makeInstrumentation = StatsdInstrumentation.make(config)

  override def runInstrumented(args: List[String], inst: Instrumentation[Nothing]): URIO[ZEnv, ExitCode] = for {
    f <- program.fork
    _ <- getStrLn.catchAll(_ => ZIO.succeed(""))
    _ <- f.interrupt
  } yield (ExitCode.success)

}
