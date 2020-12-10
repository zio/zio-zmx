package zio.zmx.metrics

import zio._
import zio.stream._

import zio.zmx.metrics.MetricsDataModel._

object MetricsChannel {

  def record(m: MetricEvent)               = channel.service.flatMap(_.offer(m))
  def recordOption(m: Option[MetricEvent]) = ZIO.foreach(m)(m => record(m))

  def eventStream: ZStream[Any, Nothing, MetricEvent] = ZStream.repeatEffect(channel.service.flatMap(q => q.take))

  private lazy val channel = new Object with SingletonService[Queue[MetricEvent]] {
    override private[zmx] def makeService = Queue.unbounded[MetricEvent]
  }

}
