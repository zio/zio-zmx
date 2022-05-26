package zio.metrics

import zio._
import zio.metrics.connectors.internal.MetricsClient
import zio.metrics.connectors.statsd._

package object connectors {

  type NewRelicConfig

  lazy val newRelic: ZLayer[NewRelicConfig & MetricsConfig, Nothing, Unit] = ???

  lazy val prometheusX: ZLayer[MetricsConfig, Nothing, Unit] = ???

  lazy val statsD: ZLayer[StatsdConfig & MetricsConfig, Nothing, Unit] = 
    ZLayer.scoped(
      StatsdClient.make.map(clt => makeConnector(statsdHandler(clt)))
    )

  def statsdHandler(clt: StatsdClient): Iterable[MetricEvent] => UIO[Unit] = events => {
    val evtFilter : MetricEvent => Boolean = {
      case unchanged: MetricEvent.Unchanged => false
      case _ => true
    }

    val send = ZIO.foreach(events.filter(evtFilter))(evt => for {
      encoded <- StatsdEncoder.encode(evt).catchAll(_ => ZIO.succeed(Chunk.empty))
      _ <- ZIO.when(encoded.nonEmpty)(ZIO.attempt(clt.write(encoded)))
    } yield()).unit

    // TODO: Do we want to at least log a problem sending the metrics ? 
    send.catchAll(_ => ZIO.unit)
  }

  def newRelic(cfg: NewRelicConfig, metricsCfg: MetricsConfig): ZLayer[Any, Nothing, Unit] = ???

  // Replacement for MetricClient
  private def makeConnector(
    onUpdate: Iterable[MetricEvent] => UIO[Unit],
  ): ZIO[Scope & MetricsConfig, Nothing, Unit] = 
    MetricsClient.make(onUpdate)

}
