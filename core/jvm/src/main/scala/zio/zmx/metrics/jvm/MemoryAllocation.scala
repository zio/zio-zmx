package zio.zmx.metrics.jvm

import com.sun.management.GarbageCollectionNotificationInfo
import zio.clock.Clock
import zio.system.System
import zio.zmx.metrics.{ MetricAspect, MetricsSyntax }
import zio.{ Runtime, Task, UIO, ZIO, ZManaged }

import java.lang.management.ManagementFactory
import javax.management.openmbean.CompositeData
import javax.management.{ Notification, NotificationEmitter, NotificationListener }
import scala.collection.mutable
import scala.collection.JavaConverters._

object MemoryAllocation extends JvmMetrics {

  /** Total bytes allocated in a given JVM memory pool. Only updated after GC, not continuously. */
  private def countAllocations(pool: String): MetricAspect[Long] =
    MetricAspect.countValueWith("jvm_memory_pool_allocated_bytes_total", "pool" -> pool)(_.toDouble)

  private class Listener(runtime: Runtime[Any]) extends NotificationListener {
    private val lastMemoryUsage: mutable.Map[String, Long] = mutable.HashMap.empty

    override def handleNotification(notification: Notification, handback: Any): Unit = {
      val info                =
        GarbageCollectionNotificationInfo.from(notification.getUserData.asInstanceOf[CompositeData])
      val gcInfo              = info.getGcInfo
      val memoryUsageBeforeGc = gcInfo.getMemoryUsageBeforeGc
      val memoryUsageAfterGc  = gcInfo.getMemoryUsageAfterGc
      for (entry <- memoryUsageBeforeGc.entrySet.asScala) {
        val memoryPool = entry.getKey
        val before     = entry.getValue.getUsed
        val after      = memoryUsageAfterGc.get(memoryPool).getUsed
        handleMemoryPool(memoryPool, before, after)
      }
    }

    private def handleMemoryPool(memoryPool: String, before: Long, after: Long): Unit = {
      /*
       * Calculate increase in the memory pool by comparing memory used
       * after last GC, before this GC, and after this GC.
       * See ascii illustration below.
       * Make sure to count only increases and ignore decreases.
       * (Typically a pool will only increase between GCs or during GCs, not both.
       * E.g. eden pools between GCs. Survivor and old generation pools during GCs.)
       *
       *                         |<-- diff1 -->|<-- diff2 -->|
       * Timeline: |-- last GC --|             |---- GC -----|
       *                      ___^__        ___^____      ___^___
       * Mem. usage vars:    / last \      / before \    / after \
       */
      // Get last memory usage after GC and remember memory used after for next time
      val last     = lastMemoryUsage.getOrElse(memoryPool, 0L)
      lastMemoryUsage.put(memoryPool, after)
      // Difference since last GC
      var diff1    = before - last
      // Difference during this GC
      var diff2    = after - before
      // Make sure to only count increases
      if (diff1 < 0) diff1 = 0
      if (diff2 < 0) diff2 = 0
      val increase = diff1 + diff2
      if (increase > 0) {
        runtime.unsafeRun {
          (UIO(increase) @@ countAllocations(memoryPool)).unit
        }
      }
    }
  }

  override val collectMetrics: ZManaged[Clock with System, Throwable, Unit] =
    ZManaged
      .make(
        for {
          runtime                 <- ZIO.runtime[Any]
          listener                 = new Listener(runtime)
          garbageCollectorMXBeans <- Task(ManagementFactory.getGarbageCollectorMXBeans.asScala)
          _                       <- ZIO.foreach_(garbageCollectorMXBeans) {
                                       case emitter: NotificationEmitter =>
                                         Task(emitter.addNotificationListener(listener, null, null))
                                       case _                            => ZIO.unit
                                     }
        } yield (listener, garbageCollectorMXBeans)
      ) { case (listener, garbageCollectorMXBeans) =>
        ZIO
          .foreach_(garbageCollectorMXBeans) {
            case emitter: NotificationEmitter =>
              Task(emitter.removeNotificationListener(listener))
            case _                            => ZIO.unit
          }
          .orDie
      }
      .unit
}
