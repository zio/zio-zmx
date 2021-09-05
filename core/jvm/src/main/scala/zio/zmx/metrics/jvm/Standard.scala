package zio.zmx.metrics.jvm

import zio.blocking.Blocking
import zio.clock.Clock
import zio.system.System
import zio.zmx.metrics._
import zio.{ Chunk, Task, ZIO, ZManaged }

import java.lang.management.{ ManagementFactory, PlatformManagedObject, RuntimeMXBean }
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import scala.util.{ Failure, Success, Try }

object Standard extends JvmMetrics {

  /** Total user and system CPU time spent in seconds. */
  private val cpuSecondsTotal: MetricAspect[Long] =
    MetricAspect.setGaugeWith("process_cpu_seconds_total")(_.toDouble / 1.0e09)

  /** Start time of the process since unix epoch in seconds. */
  private val processStartTime: MetricAspect[Long] =
    MetricAspect.setGaugeWith("process_start_time_seconds")(_.toDouble / 1000.0)

  /** Number of open file descriptors. */
  private val openFdCount: MetricAspect[Long] =
    MetricAspect.setGaugeWith("process_open_fds")(_.toDouble)

  /** Maximum number of open file descriptors. */
  private val maxFdCount: MetricAspect[Long] =
    MetricAspect.setGaugeWith("process_max_fds")(_.toDouble)

  /** Virtual memory size in bytes. */
  private val virtualMemorySize: MetricAspect[Double] =
    MetricAspect.setGauge("process_virtual_memory_bytes")

  /** Resident memory size in bytes. */
  private val residentMemorySize: MetricAspect[Double] =
    MetricAspect.setGauge("process_resident_memory_bytes")

  class MXReflection(getterName: String, obj: PlatformManagedObject) {
    private val cls: Class[_ <: PlatformManagedObject] = obj.getClass
    private val method: Option[Method]                 = findGetter(Try(cls.getMethod(getterName)))

    def isAvailable: Boolean = method.isDefined

    def unsafeGet: Task[Long] =
      method match {
        case Some(getter) => Task(getter.invoke(obj).asInstanceOf[Long])
        case None         =>
          ZIO.fail(new IllegalStateException(s"MXReflection#get called on unavailable metri"))
      }

    private def findGetter(getter: Try[Method]): Option[Method] =
      getter match {
        case Failure(_)      =>
          None
        case Success(method) =>
          try {
            val _ = method.invoke(obj).asInstanceOf[Long]
            Some(method)
          } catch {
            case _: IllegalAccessException =>
              method.getDeclaringClass.getInterfaces.toStream.flatMap { iface =>
                findGetter(Try(iface.getMethod(getterName)))
              }.headOption
          }
      }
  }

  private def reportStandardMetrics(
    runtimeMXBean: RuntimeMXBean,
    getProcessCPUTime: MXReflection,
    getOpenFileDescriptorCount: MXReflection,
    getMaxFileDescriptorCount: MXReflection,
    isLinux: Boolean
  ): ZIO[Blocking, Throwable, Unit] =
    for {
      _ <- (getProcessCPUTime.unsafeGet @@ cpuSecondsTotal).when(getProcessCPUTime.isAvailable)
      _ <- Task(runtimeMXBean.getStartTime) @@ processStartTime
      _ <- (getOpenFileDescriptorCount.unsafeGet @@ openFdCount).when(
             getOpenFileDescriptorCount.isAvailable
           )
      _ <- (getMaxFileDescriptorCount.unsafeGet @@ maxFdCount).when(
             getMaxFileDescriptorCount.isAvailable
           )
      _ <- collectMemoryMetricsLinux().when(isLinux)
    } yield ()

  private def collectMemoryMetricsLinux(): ZIO[Blocking, Throwable, Unit] =
    ZManaged.readFile("/proc/self/status").use { stream =>
      stream
        .readAll(8192)
        .catchAll {
          case None        => ZIO.succeed(Chunk.empty)
          case Some(error) => ZIO.fail(error)
        }
        .flatMap { bytes =>
          Task(new String(bytes.toArray, StandardCharsets.US_ASCII)).flatMap { raw =>
            ZIO.foreach_(raw.split('\n')) { line =>
              if (line.startsWith("VmSize:")) {
                Task(line.split("\\s+")(1).toDouble * 1024.0) @@ virtualMemorySize
              } else if (line.startsWith("VmRSS:")) {
                Task(line.split("\\s+")(1).toDouble * 1024.0) @@ residentMemorySize
              } else {
                ZIO.unit
              }
            }
          }
        }
    }

  override val collectMetrics: ZManaged[Clock with System with Blocking, Throwable, Unit] =
    ZManaged.make {
      for {
        runtimeMXBean             <- Task(ManagementFactory.getRuntimeMXBean)
        operatingSystemMXBean     <- Task(ManagementFactory.getOperatingSystemMXBean)
        getProcessCpuTime          = new MXReflection("getProcessCpuTime", operatingSystemMXBean)
        getOpenFileDescriptorCount =
          new MXReflection("getOpenFileDescriptorCount", operatingSystemMXBean)
        getMaxFileDescriptorCount  =
          new MXReflection("getMaxFileDescriptorCount", operatingSystemMXBean)
        isLinux                   <- Task(operatingSystemMXBean.getName.indexOf("Linux") == 0)
        fiber                     <-
          reportStandardMetrics(
            runtimeMXBean,
            getProcessCpuTime,
            getOpenFileDescriptorCount,
            getMaxFileDescriptorCount,
            isLinux
          )
            .repeat(collectionSchedule)
            .interruptible
            .forkDaemon
      } yield fiber
    }(_.interrupt).unit
}
