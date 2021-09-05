package zio.zmx.metrics.jvm
import zio.clock.Clock
import zio.zmx.metrics.{ MetricAspect, MetricsSyntax }
import zio.{ system, Task, UIO, ZIO, ZManaged }

import java.lang.management.{ ManagementFactory, ThreadMXBean }

object Thread extends JvmMetrics {

  /** Current thread count of a JVM */
  private val threadsCurrent: MetricAspect[Int] =
    MetricAspect.setGaugeWith("jvm_threads_current")(_.toDouble)

  /** Daemon thread count of a JVM */
  private val threadsDaemon: MetricAspect[Int] =
    MetricAspect.setGaugeWith("jvm_threads_daemon")(_.toDouble)

  /** Peak thread count of a JVM */
  private val threadsPeak: MetricAspect[Int] =
    MetricAspect.setGaugeWith("jvm_threads_peak")(_.toDouble)

  /** Started thread count of a JVM */
  private val threadsStartedTotal: MetricAspect[Long] =
    MetricAspect.setGaugeWith("jvm_threads_started_total")(
      _.toDouble
    ) // NOTE: this is a counter in the prometheus hotspot library (but explicitly set to an actual value)

  /** Cycles of JVM-threads that are in deadlock waiting to acquire object monitors or ownable synchronizers */
  private val threadsDeadlocked: MetricAspect[Int] =
    MetricAspect.setGaugeWith("jvm_threads_deadlocked")(_.toDouble)

  /** Cycles of JVM-threads that are in deadlock waiting to acquire object monitors */
  private val threadsDeadlockedMonitor: MetricAspect[Int] =
    MetricAspect.setGaugeWith("jvm_threads_deadlocked_monitor")(_.toDouble)

  /** Current count of threads by state */
  private def threadsState(state: java.lang.Thread.State): MetricAspect[Long] =
    MetricAspect.setGaugeWith("jvm_threads_state", "state" -> state.name())(_.toDouble)

  private def getThreadStateCounts(
    threadMXBean: ThreadMXBean
  ): Task[Map[java.lang.Thread.State, Long]]                                  =
    for {
      allThreads <- Task(threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds, 0))
      initial     = java.lang.Thread.State.values().map(_ -> 0L).toMap
      result      = allThreads.foldLeft(initial) { (result, thread) =>
                      if (thread != null) {
                        result.updated(thread.getThreadState, result(thread.getThreadState) + 1)
                      } else result
                    }
    } yield result

  private def reportThreadMetrics(threadMXBean: ThreadMXBean): ZIO[Any, Throwable, Unit] =
    for {
      _                 <- Task(threadMXBean.getThreadCount) @@ threadsCurrent
      _                 <- Task(threadMXBean.getDaemonThreadCount) @@ threadsDaemon
      _                 <- Task(threadMXBean.getPeakThreadCount) @@ threadsPeak
      _                 <- Task(threadMXBean.getTotalStartedThreadCount) @@ threadsStartedTotal
      _                 <- Task(
                             Option(threadMXBean.findDeadlockedThreads()).map(_.length).getOrElse(0)
                           ) @@ threadsDeadlocked
      _                 <- Task(
                             Option(threadMXBean.findMonitorDeadlockedThreads()).map(_.length).getOrElse(0)
                           ) @@ threadsDeadlockedMonitor
      threadStateCounts <- getThreadStateCounts(threadMXBean)
      _                 <- ZIO.foreach_(threadStateCounts) { case (state, count) =>
                             UIO(count) @@ threadsState(state)
                           }
    } yield ()

  override val collectMetrics: ZManaged[Clock with system.System, Throwable, Unit] =
    ZManaged.make {
      for {
        threadMXBean <- Task(ManagementFactory.getThreadMXBean)
        fiber        <-
          reportThreadMetrics(threadMXBean).repeat(collectionSchedule).interruptible.forkDaemon
      } yield fiber
    }(_.interrupt).unit
}
