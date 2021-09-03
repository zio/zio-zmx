package zio.zmx.metrics.jvm

import zio.{ Schedule, ZManaged }
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.system.System

trait JvmMetrics {
  protected val collectionSchedule: Schedule[Any, Any, Unit] = Schedule.fixed(10.seconds).unit

  val collectMetrics: ZManaged[Clock with System with Blocking, Throwable, Unit]
}
