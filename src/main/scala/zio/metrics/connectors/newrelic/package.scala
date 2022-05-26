package zio.metrics.connectors

import zio._
import zio.metrics.connectors.internal.MetricsClient

package object newrelic {

  lazy val newRelicLayer: ZLayer[MetricsConfig with NewRelicConfig,Nothing,Unit] = 
    ZLayer.scoped( for {
      encoder <- newRelicEncoder
      client <- NewRelicClient.make
      handler = newRelicHandler(encoder, client)
      _ <- MetricsClient.make(handler)
    } yield ()
  )

  private lazy val newRelicEncoder = 
    Clock.instant.map(NewRelicEncoder.apply)

  private def newRelicHandler(
    encoder: NewRelicEncoder,
    client: NewRelicClient
  ) : Iterable[MetricEvent] => UIO[Unit] = events => {
    val evtFilter : MetricEvent => Boolean = {
      case MetricEvent.Unchanged(_, _, _) => false
      case _ => true
    }

    val send = ZIO.foreach(events.filter(evtFilter))(evt => for { 
      encoded <- encoder.encode(evt).catchAll(_ => ZIO.succeed(Chunk.empty))
      _ <- ZIO.when(encoded.nonEmpty)(client.send(encoded))
    } yield ()).unit  

    send
    
  }
  
}

