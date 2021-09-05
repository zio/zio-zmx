package zio.zmx.metrics.jvm
import zio.clock.Clock
import zio.system._
import zio.zmx.metrics.{ MetricAspect, MetricsSyntax }
import zio.{ system, ZIO, ZManaged }

object VersionInfo extends JvmMetrics {

  /** JVM version info */
  def jvmInfo(version: String, vendor: String, runtime: String): MetricAspect[Unit] =
    MetricAspect.setGaugeWith(
      "jvm_info",
      "version" -> version,
      "vendor"  -> vendor,
      "runtime" -> runtime
    )(_ => 1.0)

  private def reportVersions(): ZIO[System, Throwable, Unit] =
    for {
      version <- system.propertyOrElse("java.runtime.version", "unknown")
      vendor  <- system.propertyOrElse("java.vm.vendor", "unknown")
      runtime <- system.propertyOrElse("java.runtime.name", "unknown")
      _       <- ZIO.unit @@ jvmInfo(version, vendor, runtime)
    } yield ()

  override val collectMetrics: ZManaged[Clock with System, Throwable, Unit] =
    ZManaged.make {
      for {
        fiber <- reportVersions().repeat(collectionSchedule).interruptible.forkDaemon
      } yield fiber
    }(_.interrupt).unit
}
