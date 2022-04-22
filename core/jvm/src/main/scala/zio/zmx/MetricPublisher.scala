package zio.zmx

import zio._
import zio.zmx.newrelic.NewRelicPublisher

import MetricPublisher.Result
import zhttp.service._
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

  def publish[A: Tag](metrics: Iterable[A]) =
    ZIO.serviceWithZIO[MetricPublisher[A]](_.publish(metrics))

  val newRelic = ZLayer.fromZIO {
    for {
      channelFactory <- ZIO.service[ChannelFactory]
      eventLoopGroop <- ZIO.service[EventLoopGroup]
      settings       <- ZIO.service[NewRelicPublisher.Settings]

    } yield NewRelicPublisher(channelFactory, eventLoopGroop, settings)
  }

}
