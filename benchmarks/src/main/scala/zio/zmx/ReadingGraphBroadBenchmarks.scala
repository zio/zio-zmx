package zio.zmx

import org.openjdk.jmh.annotations._
import zio.zmx.ZMXRuntime._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ReadingGraphBroadBenchmarks {
  @Param(Array("100"))
  var size: Int = _

  @Setup(Level.Invocation) def setup(): Unit = {
    unsafeRunToFuture(broad(size))
  }

  @Benchmark
  def readingBroad(): Unit = {
    unsafeRun(ZMXSupervisor.value)
  }
}
