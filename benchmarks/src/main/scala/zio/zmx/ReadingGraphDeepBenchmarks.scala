package zio.zmx

import org.openjdk.jmh.annotations._
import zio.zmx.ZMXRuntime._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ReadingGraphDeepBenchmarks {
  @Param(Array("100000"))
  var size: Int = _

  @Setup(Level.Invocation) def setup(): Unit =
    unsafeRunToFuture(deep(size))

  @Benchmark
  def readingDeep(): Unit =
    unsafeRun(ZMXSupervisor.value)
}
