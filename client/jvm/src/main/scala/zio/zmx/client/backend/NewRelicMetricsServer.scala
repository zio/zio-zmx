package zio.zmx.client.backend

import zio._
import zio.zmx.newrelic.NewRelicApp

object NewRelicMetricsServer extends NewRelicApp.Default() {

  override def run = {
    val runtimeDuration = 2.minutes

    val runSample =
      for {
        _ <- Console.printLine("Starting Simple New Relic Example...")
        _ <- Console.printLine("Simple New Relic Example started!")
        _ <- InstrumentedSample.program.fork
        _ <- Console.printLine(s"Running Instrumented Sample for ${runtimeDuration.toSeconds} seconds.")
        f <- ZIO.unit.schedule(Schedule.duration(runtimeDuration)).fork
        _ <- f.join
      } yield ()

    runSample
  }
}
