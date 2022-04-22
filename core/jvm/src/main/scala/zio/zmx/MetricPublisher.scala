package zio.zmx

import zio._
trait MetricPublisher[A] {

  /**
   * Start publishing a new complete Snapshot
   */
  def startSnapshot(implicit trace: ZTraceElement): UIO[Unit] = ZIO.unit

  /**
   * Finish publishing a new complete Snapshot
   */
  def completeSnapshot(implicit trace: ZTraceElement): UIO[Unit] = ZIO.unit

  /**
   * Called by the MetricListener to publish the events associated with a single metric
   */
  def publish(metrics: Iterable[A]): ZIO[Any, Nothing, MetricPublisher.Result]
}

object MetricPublisher {

  sealed trait Result extends Any with Product with Serializable

  object Result {

    case object Success extends Result

    case class TerminalFailure(e: Throwable) extends Result

    case class TransientFailure(e: Throwable) extends Result
  }

}
