package zio.zmx.metrics.jvm

import zio.clock.Clock
import zio.zmx.metrics.{ MetricAspect, MetricsSyntax }
import zio.{ Task, UIO, ZIO, ZManaged }

import java.lang.management.{ ManagementFactory, MemoryMXBean, MemoryPoolMXBean, MemoryUsage }
import scala.collection.JavaConverters._

object MemoryPools extends JvmMetrics {

  sealed private trait Area { val label: String }
  private case object Heap    extends Area { override val label: String = "heap"    }
  private case object NonHeap extends Area { override val label: String = "nonheap" }

  /** Used bytes of a given JVM memory area. */
  private def memoryBytesUsed(area: Area): MetricAspect[Long]                            =
    MetricAspect.setGaugeWith("jvm_memory_bytes_used", "area" -> area.label)(_.toDouble)

  /** Committed (bytes) of a given JVM memory area. */
  private def memoryBytesCommitted(area: Area): MetricAspect[Long]                       =
    MetricAspect.setGaugeWith("jvm_memory_bytes_committed", "area" -> area.label)(_.toDouble)

  /** Max (bytes) of a given JVM memory area. */
  private def memoryBytesMax(area: Area): MetricAspect[Long]                             =
    MetricAspect.setGaugeWith("jvm_memory_bytes_max", "area" -> area.label)(_.toDouble)

  /** Initial bytes of a given JVM memory area. */
  private def memoryBytesInit(area: Area): MetricAspect[Long]                            =
    MetricAspect.setGaugeWith("jvm_memory_bytes_init", "area" -> area.label)(_.toDouble)

  /** Used bytes of a given JVM memory pool. */
  private def poolBytesUsed(pool: String): MetricAspect[Long]                            =
    MetricAspect.setGaugeWith("jvm_memory_pool_bytes_used", "pool" -> pool)(_.toDouble)

  /** Committed bytes of a given JVM memory pool. */
  private def poolBytesCommitted(pool: String): MetricAspect[Long]                       =
    MetricAspect.setGaugeWith("jvm_memory_pool_bytes_committed", "pool" -> pool)(_.toDouble)

  /** Max bytes of a given JVM memory pool. */
  private def poolBytesMax(pool: String): MetricAspect[Long]                             =
    MetricAspect.setGaugeWith("jvm_memory_pool_bytes_max", "pool" -> pool)(_.toDouble)

  /** Initial bytes of a given JVM memory pool. */
  private def poolBytesInit(pool: String): MetricAspect[Long]                            =
    MetricAspect.setGaugeWith("jvm_memory_pool_bytes_init", "pool" -> pool)(_.toDouble)

  private def reportMemoryUsage(usage: MemoryUsage, area: Area): ZIO[Any, Nothing, Unit] =
    for {
      _ <- UIO(usage.getUsed) @@ memoryBytesUsed(area)
      _ <- UIO(usage.getCommitted) @@ memoryBytesCommitted(area)
      _ <- UIO(usage.getMax) @@ memoryBytesMax(area)
      _ <- UIO(usage.getInit) @@ memoryBytesInit(area)
    } yield ()

  private def reportPoolUsage(usage: MemoryUsage, pool: String): ZIO[Any, Nothing, Unit] =
    for {
      _ <- UIO(usage.getUsed) @@ poolBytesUsed(pool)
      _ <- UIO(usage.getCommitted) @@ poolBytesCommitted(pool)
      _ <- UIO(usage.getMax) @@ poolBytesMax(pool)
      _ <- UIO(usage.getInit) @@ poolBytesInit(pool)
    } yield ()

  private def reportMemoryMetrics(
    memoryMXBean: MemoryMXBean,
    poolMXBeans: List[MemoryPoolMXBean]
  ): ZIO[Any, Throwable, Unit] =
    for {
      heapUsage    <- Task(memoryMXBean.getHeapMemoryUsage)
      nonHeapUsage <- Task(memoryMXBean.getNonHeapMemoryUsage)
      _            <- reportMemoryUsage(heapUsage, Heap)
      _            <- reportMemoryUsage(nonHeapUsage, NonHeap)
      _            <- ZIO.foreachPar_(poolMXBeans) { pool =>
                        for {
                          name  <- Task(pool.getName)
                          usage <- Task(pool.getUsage)
                          _     <- reportPoolUsage(usage, name)
                        } yield ()
                      }
    } yield ()

  val collectMetrics: ZManaged[Clock, Throwable, Unit] =
    ZManaged.make {
      for {
        memoryMXBean <- Task(ManagementFactory.getMemoryMXBean)
        poolMXBeans  <- Task(ManagementFactory.getMemoryPoolMXBeans.asScala.toList)
        fiber        <-
          reportMemoryMetrics(memoryMXBean, poolMXBeans)
            .repeat(collectionSchedule)
            .interruptible
            .forkDaemon
      } yield fiber
    }(_.interrupt).unit
}
