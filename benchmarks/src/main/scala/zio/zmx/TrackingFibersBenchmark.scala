package zio.zmx

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import ZMXBenchmarks.Runtime.unsafeRun
import zio._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TrackingFibersBenchmark {
  @Param(Array("100000"))
  var size: Int = _

  def spawn(i: Int): ZIO[Any, Nothing, Fiber.Runtime[Nothing, Unit]] =
    if (i <= 0) {
      ZIO.never.fork
    } else
      for {
        rec <- spawn(i - 1).fork
        f   <- rec.join
      } yield f

  val deep = {
    for {
      _ <- spawn(size)
    } yield ()
  }

  val broad =
    for {
      _ <- ZIO.foreach(1 to size)(_ => ZIO.never.fork)
    } yield ()

  val mixed = {
    for {
      _ <- ZIO.foreach(1 to (size / 100))(_ => spawn(size / 100))
    } yield ()
  }

  @Benchmark
  def zmxBroad(): Unit =
    unsafeRun(broad)

  @Benchmark
  def defaultBroad(): Unit =
    Runtime.default.unsafeRun(broad)

  @Benchmark
  def zmxDeep(): Unit =
    unsafeRun(deep)

  @Benchmark
  def defaultDeep(): Unit =
    Runtime.default.unsafeRun(deep)

  @Benchmark
  def zmxMixed(): Unit =
    unsafeRun(mixed)

  @Benchmark
  def defaultMixed(): Unit =
    Runtime.default.unsafeRun(mixed)

}
