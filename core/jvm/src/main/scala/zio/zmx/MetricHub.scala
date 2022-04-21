package zio.zmx

import java.time.Instant

import zio._
import zio.internal.metrics._
import zio.metrics._
import zio.stream.ZStream

trait MetricHub {
  def eventStream: ZStream[Any, Nothing, MetricHub.Event]

  def publishMetric(metricPair: MetricPair.Untyped, timestamp: Instant): ZIO[Any, Throwable, Unit]

  def subscribe: ZIO[Scope, Nothing, Dequeue[MetricHub.Event]]

}

object MetricHub {

  sealed trait Event

  object Event {

    final case class New(metric: MetricPair.Untyped, timestamp: Instant) extends Event

    final case class Unchanged(metricPair: MetricPair.Untyped, timestamp: Instant) extends Event

    final case class Updated(
      metricKey: MetricKey.Untyped,
      currentState: MetricState.Untyped,
      updatedState: MetricState.Untyped,
      timestamp: Instant)
        extends Event
  }

  final case class PollingMetricHub(fiber: Fiber[Throwable, Unit], metricHub: MetricHub)

  def polling = ZLayer.fromZIO {

    def poller(metricHub: MetricHub): ZIO[Any, Throwable, Unit] = {
      val pgm = for {
        snapshot <- ZIO.attempt(metricRegistry.snapshot())
        now <- Clock.instant // TODO: Obviously incorrect, the upstream metric registry will need to provide this.
        _        <- ZIO.foreach(snapshot)(metricHub.publishMetric(_, now))
      } yield ()

      pgm.repeat(Schedule.spaced(5.second)).unit
    }

    for {
      hub        <- Hub.sliding[MetricHub.Event](1000)
      historyRef <- Ref.make(Map.empty[MetricKey.Untyped, (Long, MetricState.Untyped)])
      metricHub   = LiveMetricHub(historyRef, hub)
      fiber      <- poller(metricHub).fork

    } yield PollingMetricHub(fiber, metricHub)

  }

}

final case class LiveMetricHub(
  historyRef: Ref[Map[MetricKey.Untyped, (Long, MetricState.Untyped)]],
  hub: Hub[MetricHub.Event])
    extends MetricHub {

  override def eventStream = ZStream.fromHub(hub)

  override def publishMetric(metricPair: MetricPair.Untyped, timestamp: Instant) = {
    val makeEvent: UIO[MetricHub.Event] = historyRef.modify { history =>
      history.get(metricPair.metricKey) match {
        case Some((lastTimestamp, lastState)) =>
          if (lastTimestamp < timestamp.toEpochMilli) {
            val history2 = history + (metricPair.metricKey -> (timestamp.toEpochMilli(), metricPair.metricState))
            MetricHub.Event
              .Updated(metricPair.metricKey, lastState, metricPair.metricState, timestamp) -> history2

          } else MetricHub.Event.Unchanged(metricPair, timestamp) -> history
        case None                             =>
          val history2 = history + (metricPair.metricKey -> (timestamp.toEpochMilli(), metricPair.metricState))
          MetricHub.Event.New(metricPair, timestamp) -> history2

      }

    }

    makeEvent.flatMap(hub.offer).unit // TODO: What should we do if 'offer' retruns 'false'?
  }

  override def subscribe = hub.subscribe

}
