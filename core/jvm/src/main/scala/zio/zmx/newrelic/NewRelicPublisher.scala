package zio.zmx.newrelic

import java.util.concurrent.atomic.AtomicReference

import zio._
import zio.metrics.MetricPair
import zio.stream.ZStream

trait NewRelicPublisher {

  def runPublisher: ZIO[Any, Nothing, Fiber[Throwable, Unit]]

  def unsafePublish(pair: MetricPair.Untyped): Unit
}

object NewRelicPublisher {

  val live = ZLayer {
    for {
      client  <- ZIO.service[NewRelicClient]
      encoder <- ZIO.service[NewRelicEncoder]
    } yield LiveNewRelicPublisher(client, encoder)
  }
}

final case class LiveNewRelicPublisher(
  client: NewRelicClient,
  encoder: NewRelicEncoder)
    extends NewRelicPublisher {

  private val buffer = new AtomicReference[Chunk[MetricPair.Untyped]](Chunk.empty)

  def runPublisher: ZIO[Any, Nothing, Fiber[Throwable, Unit]] =
    ZStream
      .tick(5.seconds)               // TODO: Should this be configurable?
      .map { _ =>
        val pairs = getCompareAndSet(None)
        encoder.encodeMetrics(pairs, java.lang.System.currentTimeMillis())
      }
      .flattenChunks
      .groupedWithin(1000, 1.minute) // TODO: make this configurable.
      .mapZIO(client.sendMetrics)
      .runDrain
      .fork

  def unsafePublish(pair: MetricPair.Untyped): Unit = {
    getCompareAndSet(Some(pair))
    ()
  }

  private def getCompareAndSet(pair: Option[MetricPair.Untyped]): Chunk[MetricPair.Untyped] = {
    var loop                               = true
    var current: Chunk[MetricPair.Untyped] = Chunk.empty

    while (loop) {
      current = buffer.get()
      pair match {
        case None       =>
          // `None` indicates we must clear the current buffer.
          loop = !buffer.compareAndSet(current, Chunk.empty)
        case Some(pair) =>
          val updated = current :+ pair
          loop = !buffer.compareAndSet(current, updated)
      }
    }
    current
  }
}
