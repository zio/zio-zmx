package zio.metrics.connectors

import zio._
import zio.metrics.connectors.internal.MetricsClient

package object statsd {
  
  lazy val statsdLayer: ZLayer[StatsdConfig & MetricsConfig, Nothing, Unit] = 
    ZLayer.scoped(
      StatsdClient.make.map(clt => MetricsClient.make(statsdHandler(clt))).unit
    )

  private def statsdHandler(clt: StatsdClient): Iterable[MetricEvent] => UIO[Unit] = events => {
    val evtFilter : MetricEvent => Boolean = {
      case MetricEvent.Unchanged(_, _, _) => false
      case _ => true
    }

    val send = ZIO.foreach(events.filter(evtFilter))(evt => for {
      encoded <- StatsdEncoder.encode(evt).catchAll(_ => ZIO.succeed(Chunk.empty))
      _ <- ZIO.when(encoded.nonEmpty)(ZIO.attempt(clt.send(encoded)))
    } yield()).unit

    // TODO: Do we want to at least log a problem sending the metrics ? 
    send.catchAll(_ => ZIO.unit)
  }
}

