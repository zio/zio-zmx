package zio.metrics.connectors.internal

import scala.annotation.nowarn

import zio._ 
import zio.metrics._
import zio.metrics.connectors._
import zio.internal.metrics.metricRegistry

object MetricsClient{

  def make(handler: Iterable[MetricEvent] => UIO[Unit]): ZIO[Scope & MetricsConfig, Nothing, Unit] = 
    for {
      cfg <- ZIO.service[MetricsConfig]
      state <- Ref.make[Set[MetricPair.Untyped]](Set.empty)
      clt = new MetricsClient(cfg, state, handler){}
      _ <- clt.run
    } yield (())
    
}

private [connectors] sealed abstract class MetricsClient(
  metricsCfg: MetricsConfig,
  latestSnapshot : Ref[Set[MetricPair.Untyped]],
  handler: Iterable[MetricEvent] => UIO[Unit]
) {

    private def update(implicit trace: Trace): UIO[Unit] = 
      retrieveNext.flatMap(handler)

    private def retrieveNext(
      implicit trace: Trace,
    ): UIO[Set[MetricEvent]] = for {
      ts  <- ZIO.clockWith(_.instant)
      res <- latestSnapshot.modify { old =>
               // first we get the state for all metrics that we had captured in the last run
               val oldMap = stateMap(old)
               // then we get the snapshot from the underlying metricRegistry
               val next   = metricRegistry.snapshot()
               val res    = events(oldMap, next)
               (res, next)
             }

    } yield res

    // This will create a map for the metrics captured in the last snapshot
    private def stateMap(metrics: Set[MetricPair.Untyped]): Map[MetricKey.Untyped, MetricState.Untyped] = {

      val builder = scala.collection.mutable.Map[MetricKey.Untyped, MetricState.Untyped]()
      val it      = metrics.iterator
      while (it.hasNext) {
        val e = it.next()
        builder.update(e.metricKey, e.metricState)
      }

      builder.toMap
    }

    private def events(
      oldState: Map[MetricKey.Untyped, MetricState.Untyped],
      metrics: Set[MetricPair.Untyped],
    )(implicit @nowarn trace: Trace,
    ): Set[MetricEvent] =
      metrics
        .map { mp =>
          MetricEvent.make(mp.metricKey, oldState.get(mp.metricKey), mp.metricState)
        }
        .collect { case Right(e) => e }

    def run(implicit trace: Trace) =
      update
        .scheduleFork(Schedule.fixed(metricsCfg.interval))
        .unit

}
