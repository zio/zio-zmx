/*
 * Copyright 2020-2022 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.zmx

import scala.annotation.nowarn

import zio._
import zio.internal.metrics._
import zio.metrics._
import zio.zmx.newrelic.NewRelicListener
import zio.zmx.newrelic.NewRelicPublisher

/**
 * A `MetricClient` provides the functionality to consume metrics produced by
 * ZIO applications. `MetricClient` supports two ways of consuming metrics,
 * corresponding to the two ways that third party metrics services use metrics.
 *
 * First, metrics services can query the latest snapshot that has been extracted
 * from the underlying metric registry.
 *
 * Second, metrics services can install a listener that will be called with the
 * complete set of metrics whenever it has been updated from the underlying
 * registry.
 *
 * The default implementation abstracts the underlying, performance optimized
 * API so that it is easier to implement arbitrary metric backends. The default
 * implementation queries the underlying metric registry on a regular basis and
 * caches the result, so that subsequent snapshot() calls will not NOT trigger
 * any activity on the metric registry itself.
 *
 * As a consequence, calls to snapshot() will not yield the most recent value.
 * From a users perspective this should be acceptable as most metric backends
 * operate on a scheduled refresh interval.
 */
trait MetricClient {

  /**
   * Get the most recent snapshot. Tis method is typically used by backend
   * implementations that require the current metric state on demand - such as
   * Prometheus.
   */
  def snapshot(implicit trace: Trace): UIO[Set[MetricPair.Untyped]]

  /**
   * Register a new listener that can consume metrics. The most common use case
   * is to push these metrics to a backend in the backend specific format.
   */
  def registerListener(listener: MetricListener[_])(implicit trace: Trace): UIO[Unit]

  /**
   * Deregister a metric listener.
   */
  def deregisterListener(listener: MetricListener[_])(implicit trace: Trace): UIO[Unit]
}

object MetricClient {

  def run = ZIO.service[ZIOMetricClient].flatMap(_.run)

  def registerListener(l: MetricListener[_]) = ZIO.serviceWithZIO[MetricClient](_.registerListener(l))

  def registerNewRelicListener() = for {
    listener  <- ZIO.service[NewRelicListener]
    publisher <- ZIO.service[NewRelicPublisher]
    _         <- publisher.run
    _         <- registerListener(listener)
  } yield ()

  final case class Settings(
    pollingInterval: Duration)

  object Settings {

    val default = EnvVar
      .duration("ZMX_METRIC_CLIENT_POLLING_INTERVAL", "MetricClient#Settings")
      .getWithDefault(10.seconds)
      .map(Settings(_))

    val live = ZLayer.fromZIO(default)  
  }

  class ZIOMetricClient private[MetricClient] (
    settings: MetricClient.Settings,
    listeners: Ref[Chunk[MetricListener[_]]],
    latestSnapshot: Ref[Set[MetricPair.Untyped]])
      extends MetricClient {

    def deregisterListener(l: MetricListener[_])(implicit trace: Trace): UIO[Unit] =
      listeners.update(cur => cur.filterNot(_.equals(l)))

    def registerListener(l: MetricListener[_])(implicit trace: Trace): UIO[Unit] =
      listeners.update(cur => cur :+ l)

    def snapshot(implicit trace: Trace): UIO[Set[MetricPair.Untyped]] =
      latestSnapshot.get

    private def update(implicit trace: Trace): UIO[Unit] = for {
      next       <- retrieveNext
      registered <- listeners.get
      _          <- ZIO.foreachPar(registered)(l => l.update(next))
    } yield ()

    private def retrieveNext(
      implicit
      trace: Trace,
    ): UIO[Set[MetricEvent]] = for {
      ts  <- ZIO.clockWith(_.instant)
      res <- latestSnapshot.modify { old =>
               // first we get the state for all the counters that we had captured in the last run
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

    def run(implicit trace: Trace): UIO[Unit] =
      update
        .schedule(Schedule.fixed(settings.pollingInterval))
        .forkDaemon
        .unit

  }

  def live(implicit trace: Trace) = ZLayer.fromZIO(
    for {
      settings  <- ZIO.service[Settings]
      listeners <- Ref.make[Chunk[MetricListener[_]]](Chunk.empty)
      snapshot  <- Ref.make(Set.empty[MetricPair.Untyped])
    } yield new ZIOMetricClient(settings, listeners, snapshot),
  )
}
