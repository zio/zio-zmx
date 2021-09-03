package zio.zmx.metrics.jvm

import zio.clock.Clock
import zio.zmx.metrics._
import zio.{ Task, ZIO, ZManaged }

import java.lang.management.{ GarbageCollectorMXBean, ManagementFactory }
import scala.jdk.CollectionConverters._

/** Exports metrics related to the garbage collector */
object GarbageCollector extends JvmMetrics {

  /** Time spent in a given JVM garbage collector in seconds. */
  private def gcCollectionSecondsSum(gc: String): MetricAspect[Long]   =
    MetricAspect.setGaugeWith("jvm_gc_collection_seconds_sum", "gc" -> gc)((ms: Long) => ms.toDouble / 1000.0)

  private def gcCollectionSecondsCount(gc: String): MetricAspect[Long] =
    MetricAspect.setGaugeWith("jvm_gc_collection_seconds_count", "gc" -> gc)(_.toDouble)

  private def reportGarbageCollectionMetrics(
    garbageCollectors: List[GarbageCollectorMXBean]
  ): ZIO[Any, Throwable, Unit]                                         =
    ZIO.foreachPar_(garbageCollectors) { gc =>
      for {
        name <- Task(gc.getName)
        _    <- Task(gc.getCollectionCount) @@ gcCollectionSecondsCount(name)
        _    <- Task(gc.getCollectionTime) @@ gcCollectionSecondsSum(name)
      } yield ()
    }

  val collectMetrics: ZManaged[Clock, Throwable, Unit] =
    ZManaged.make {
      for {
        classLoadingMXBean <- Task(ManagementFactory.getGarbageCollectorMXBeans.asScala.toList)
        fiber              <-
          reportGarbageCollectionMetrics(classLoadingMXBean)
            .repeat(collectionSchedule)
            .interruptible
            .forkDaemon
      } yield fiber
    }(_.interrupt).unit
}
