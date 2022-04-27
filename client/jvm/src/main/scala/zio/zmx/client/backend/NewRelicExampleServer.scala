package zio.zmx.client.backend

import zio._
import zio.metrics.jvm.DefaultJvmMetrics
import zio.zmx._

object NewRelicExampleServer extends ZIOAppDefault {

  val trackingFlags = RuntimeConfig.default.flags + RuntimeConfigFlag.TrackRuntimeMetrics

  val runtimeDuration = 2.minutes

  private val runSample =
    for {
      _ <- Console.printLine("Starting Metric Client...")
      _ <- MetricClient.registerNewRelicListener()
      _ <- MetricClient.run
      _ <- Console.printLine("Metric Client started!")
      _ <- InstrumentedSample.program.fork
      _ <- Console.printLine(s"Running Instrumented Sample for ${runtimeDuration.toSeconds} seconds.")
      f <- ZIO.unit.schedule(Schedule.duration(runtimeDuration)).fork
      _ <- f.join
    } yield ()

  def run =
    ZIO
      .withRuntimeConfig(RuntimeConfig.default.copy(flags = trackingFlags))(runSample)
      .provide(
        newrelic.quickStart,
        ZLayer.succeed(MetricClient.Settings.default),
        MetricClient.live,
        Clock.live,
        Random.live,
        System.live,
        Console.live,
      )

}

object NewRelicMetricSampleWithJvm extends ZIOApp.Proxy(NewRelicExampleServer <> DefaultJvmMetrics.app)
