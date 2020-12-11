package zio.zmx

import zio._
import zio.duration._
import zio.zmx.metrics._
import MetricsDataModel._

trait InstrumentedSample {

  def doSomething  = ZMX.count("myCounter", Label("foo", "bar"))(ZIO.unit)
  def doSomething2 = ZIO.unit.counted("myCounter", Label("foo", "zio"))

  def program: ZIO[ZEnv, Nothing, ExitCode] = for {
    _ <- doSomething.schedule(Schedule.spaced(100.millis).jittered).forkDaemon
    _ <- doSomething2.schedule(Schedule.spaced(200.millis).jittered).forkDaemon
  } yield ExitCode.success
}
