package zio.zmx

import zio._
import zio.console._
import zio.duration._
import zio.zmx.metrics._
import zio.zmx.statsd.StatsdInstrumentation

object StatsdInstrumentedApp extends ZmxApp with InstrumentedSample {

  private val config = MetricsConfig(
    maximumSize = 1024,
    bufferSize = 1024,
    timeout = 10.seconds,
    pollRate = 1.second,
    host = Some("172.20.96.1"),
    port = Some(8125)
  )

  override def makeInstrumentation = ZIO.succeed(new StatsdInstrumentation(config))

  override def run(args: List[String]): URIO[ZEnv, ExitCode] = for {
    f <- program.fork
    _ <- getStrLn.catchAll(_ => ZIO.succeed(""))
    _ <- f.interrupt
  } yield (ExitCode.success)

}
