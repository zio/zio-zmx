package zio.zmx.attic

import zio._
import zio.metrics._

trait MetricRegistry {

  def lastProcessingTime(key: MetricKey.Untyped): ZIO[Any, Throwable, Option[Long]]

  def updateProcessingTime(key: MetricKey.Untyped, value: Long): ZIO[Any, Throwable, Unit]

  def snapshot: ZIO[Any, Throwable, Set[MetricPair.Untyped]]

}
