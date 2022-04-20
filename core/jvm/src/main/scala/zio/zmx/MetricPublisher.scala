package zio.zmx

import zio._

import MetricPublisher.Result
trait MetricPublisher[A] {

  def publish(metrics: Iterable[A]): ZIO[Any, Nothing, Result]

}

object MetricPublisher {

  sealed trait Result extends Any with Product with Serializable

  object Result {

    case object Success extends Result

    case class TerminalFailure(e: Throwable) extends Result

    case class TransientFailure(e: Throwable) extends Result
  }

}
