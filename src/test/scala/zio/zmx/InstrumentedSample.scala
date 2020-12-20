package zio.zmx

import zio._
import zio.random._
import zio.duration._
import zio.zmx.metrics._
import MetricsDataModel._

trait InstrumentedSample {

  // Count something explicitly
  private lazy val doSomething = ZMX.count("myCounter", 1.0d, Label("effect", "count1"))

  // Manipulate an arbitrary Gauge
  private lazy val gaugeSomething = for {
    v1 <- nextDoubleBetween(0.0d, 100.0d)
    v2 <- nextDoubleBetween(-50d, 50d)
    _  <- ZMX.gauge("setGauge", v1)
    _  <- ZMX.gaugeChange("changeGauge", v2)
  } yield ()

  // Use a convenient extension to count the number of executions of an effect
  // In this particular case count how often the gauge has been set
  private lazy val doSomething2 = gaugeSomething.counted("myCounter", Label("effect", "count2"))

  def program: ZIO[ZEnv, Nothing, ExitCode] = for {
    _ <- doSomething.schedule(Schedule.spaced(100.millis)).forkDaemon
    _ <- doSomething2.schedule(Schedule.spaced(200.millis)).forkDaemon
  } yield ExitCode.success
}
