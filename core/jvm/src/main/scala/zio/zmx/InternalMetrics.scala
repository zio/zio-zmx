package zio.zmx

import zio._
import zio.metrics.jvm.DefaultJvmMetrics

/**
 * Provides a ZLayer that allows control over which internal metrics are tracked.
 */
object InternalMetrics {

  final case class Settings(enableJVMMetrics: Boolean, enableZIOMetrics: Boolean) {
    def disableJVM = copy(enableJVMMetrics = false)

    def disableZIO = copy(enableZIOMetrics = false)

    def enableJVM = copy(enableJVMMetrics = true)

    def enableZIO = copy(enableZIOMetrics = true)
  }

  object Settings {

    val default = for {
      jvm <- EnvVar.boolean("ZMX_ENABLE_JVM_TRACKING", "InternalMetrics#Settings").getWithDefault(true)
      zio <- EnvVar.boolean("ZMX_ENABLE_ZIO_TRACKING", "InternalMetrics#Settings").getWithDefault(true)
    } yield Settings(jvm, zio)

    val live = ZLayer.fromZIO(default)
  }

  val jvmTrackingEnabled =
    ZIO.serviceWith[Settings](_.enableJVMMetrics)

  val zioTrackingEnabled =
    ZIO.serviceWith[Settings](_.enableZIOMetrics)

  private[zmx] val zioRuntimeMetrics: ZLayer[Settings, Nothing, Unit] = ZLayer.scoped[Settings](
    for {
      enabledForZIO <- InternalMetrics.zioTrackingEnabled
      _             <- if (enabledForZIO)
                         FiberRef.currentRuntimeConfigFlags.locallyScopedWith(_ + RuntimeConfigFlag.TrackRuntimeMetrics)
                       else ZIO.unit
    } yield (),
  )

  private[zmx] val jvmRuntimeMetrics: ZLayer[Settings, Nothing, Unit] = for {
    enabledForJVM <- ZLayer.fromZIO(InternalMetrics.jvmTrackingEnabled)
    layer         <- (if (enabledForJVM.get) DefaultJvmMetrics.live.orDie
                      else ZLayer.empty).unit
  } yield layer

  val live = zioRuntimeMetrics ++ jvmRuntimeMetrics
}
