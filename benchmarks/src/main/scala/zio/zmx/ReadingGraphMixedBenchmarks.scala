package zio.zmx

import org.openjdk.jmh.annotations._
import zio.zmx.ZMXRuntime._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ReadingGraphMixedBenchmarks {
  @Param(Array("100"))
  var size: Int = _

  @Setup(Level.Invocation) def setup(): Unit = {
    unsafeRunToFuture(mixed(size))
  }

  @Benchmark
  def readingMixed(): Unit = {
    unsafeRun(ZMXSupervisor.value)
  }
}
