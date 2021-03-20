package zio.zmx

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import ZMXRuntime.unsafeRun
import zio._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TrackingFibersBenchmark {
  @Param(Array("100000"))
  var size: Int = _

  @Benchmark
  def zmxBroad(): Unit =
    unsafeRun(broad(size))

  @Benchmark
  def defaultBroad(): Unit =
    Runtime.default.unsafeRun(broad(size))

  @Benchmark
  def zmxDeep(): Unit =
    unsafeRun(deep(size))

  @Benchmark
  def defaultDeep(): Unit =
    Runtime.default.unsafeRun(deep(size))

  @Benchmark
  def zmxMixed(): Unit =
    unsafeRun(mixed(size))

  @Benchmark
  def defaultMixed(): Unit =
    Runtime.default.unsafeRun(mixed(size))

}