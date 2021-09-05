package zio.zmx.metrics.jvm

import zio.{ Has, ZLayer, ZManaged }
import zio.blocking.Blocking
import zio.clock.Clock
import zio.system.System

trait DefaultJvmMetrics

/** JVM metrics, compatible with the prometheus-hotspot library */
object DefaultJvmMetrics {
  val collectDefaultJvmMetrics: ZManaged[Clock with System with Blocking, Throwable, Unit] =
    (
      BufferPools.collectMetrics <&>
        ClassLoading.collectMetrics <&>
        GarbageCollector.collectMetrics <&>
        MemoryAllocation.collectMetrics <&>
        MemoryPools.collectMetrics <&>
        Standard.collectMetrics <&>
        Thread.collectMetrics <&>
        VersionInfo.collectMetrics
    ).unit

  /** Layer that starts collecting the same JVM metrics as the Prometheus Java client's default exporters */
  val live: ZLayer[Clock with System with Blocking, Throwable, Has[DefaultJvmMetrics]] =
    collectDefaultJvmMetrics.as(new DefaultJvmMetrics {}).toLayer
}
