package zio.zmx

import zio._
import zio.duration._

object StatsdInstrumentedApp extends App with InstrumentedSample {

  import metrics._

  private val config = MetricsConfigDataModel.MetricsConfig(
    maximumSize = 1024,
    bufferSize = 1024,
    timeout = 10.seconds,
    pollRate = 1.second,
    host = Some("localhost"),
    port = Some(8125)
  )

  override val backend                                       = statsd(config)
  override def run(args: List[String]): URIO[ZEnv, ExitCode] = program

}
