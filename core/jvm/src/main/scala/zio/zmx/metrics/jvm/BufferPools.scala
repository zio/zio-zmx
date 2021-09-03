package zio.zmx.metrics.jvm

import zio.clock.Clock
import zio.zmx.metrics.{ MetricAspect, MetricsSyntax }
import zio.{ Task, ZIO, ZManaged }

import java.lang.management.{ BufferPoolMXBean, ManagementFactory }
import scala.collection.JavaConverters._

object BufferPools extends JvmMetrics {

  /** Used bytes of a given JVM buffer pool. */
  private def bufferPoolUsedBytes(pool: String): MetricAspect[Long]     =
    MetricAspect.setGaugeWith("jvm_buffer_pool_used_bytes", "pool" -> pool)(_.toDouble)

  /** Bytes capacity of a given JVM buffer pool. */
  private def bufferPoolCapacityBytes(pool: String): MetricAspect[Long] =
    MetricAspect.setGaugeWith("jvm_buffer_pool_capacity_bytes", "pool" -> pool)(_.toDouble)

  /** Used buffers of a given JVM buffer pool. */
  private def bufferPoolUsedBuffers(pool: String): MetricAspect[Long]   =
    MetricAspect.setGaugeWith("jvm_buffer_pool_used_buffers", "pool" -> pool)(_.toDouble)

  private def reportBufferPoolMetrics(
    bufferPoolMXBeans: List[BufferPoolMXBean]
  ): ZIO[Any, Throwable, Unit]                                          =
    ZIO.foreach_(bufferPoolMXBeans) { bufferPoolMXBean =>
      for {
        name <- Task(bufferPoolMXBean.getName)
        _    <- Task(bufferPoolMXBean.getMemoryUsed) @@ bufferPoolUsedBytes(name)
        _    <- Task(bufferPoolMXBean.getTotalCapacity) @@ bufferPoolCapacityBytes(name)
        _    <- Task(bufferPoolMXBean.getCount) @@ bufferPoolUsedBuffers(name)
      } yield ()
    }

  val collectMetrics: ZManaged[Clock, Throwable, Unit] =
    ZManaged.make {
      for {
        bufferPoolMXBeans <-
          Task(ManagementFactory.getPlatformMXBeans(classOf[BufferPoolMXBean]).asScala.toList)
        fiber             <-
          reportBufferPoolMetrics(bufferPoolMXBeans)
            .repeat(collectionSchedule)
            .interruptible
            .forkDaemon
      } yield fiber
    }(_.interrupt).unit
}
