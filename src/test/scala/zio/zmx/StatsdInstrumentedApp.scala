package zio.zmx

import zio._
import zio.duration._
import zio.zmx.metrics._
import zio.zmx.statsd.StatsdInstrumentation

object StatsdInstrumentedApp extends ZmxApp with InstrumentedSample {

  private val config = MetricsConfig(
    maximumSize = 1024,
    bufferSize = 1024,
    timeout = 10.seconds,
    pollRate = 1.second,
    host = Some("localhost"),
    port = Some(8125)
  )

  override def makeInstrumentation = ZIO.succeed(new StatsdInstrumentation(config))

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    program

}
