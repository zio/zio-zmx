package zio.zmx.attic

import zio._
import zio.metrics._
import zio.stream._
import zio.zmx._
import zio.zmx.attic.MetricAgent.QueueType.Dropping
import zio.zmx.attic.MetricAgent.QueueType.Sliding

import izumi.reflect.Tag

trait MetricAgent[A] {

  def runAgent: ZIO[Any, Nothing, Fiber[Throwable, Unit]]
}

object MetricAgent {

  sealed trait QueueType

  object QueueType {

    /**
     * Drops newer entries if the queue is full.
     */
    case object Dropping extends QueueType

    /**
     * Drops older entries if the queue is full.
     */
    case object Sliding extends QueueType
  }

  final case class Settings(
    maxPublishingSize: Int,
    pollingInterval: Duration,
    snapshotQueueType: QueueType,
    snapshotQueueSize: Int,
    throttling: Option[Settings.Throttling] = None)

  object Settings {
    final case class Throttling(
      throttlingSize: Long,
      throttlingWindow: Duration)
  }

  def live[
    A: Tag,
  ]: ZLayer[MetricEncoder[A] with MetricPublisher[A] with MetricRegistry with Settings, Nothing, MetricAgent[A]] =
    ZLayer.fromZIO(for {
      encoder   <- ZIO.service[MetricEncoder[A]]
      publisher <- ZIO.service[MetricPublisher[A]]
      recording <- ZIO.service[MetricRegistry]
      settings  <- ZIO.service[Settings]
    } yield LiveMetricAgent(encoder, publisher, recording, settings))
}

final case class LiveMetricAgent[A](
  encoder: MetricEncoder[A],
  publisher: MetricPublisher[A],
  registry: MetricRegistry,
  settings: MetricAgent.Settings)
    extends MetricAgent[A] {

  def runAgent: ZIO[Any, Nothing, Fiber[Throwable, Unit]] =
    for {
      processingHistory <- Ref.make(Map.empty[MetricKey.Untyped, Long])
      mutex             <- Semaphore.make(1)
      fiber             <- bufferedStream
                             .mapZIO { snapshot =>
                               mutex.withPermit { // We only want to process one snapshot at a time.
                                 for {
                                   encoded <- encode(processingHistory, snapshot)
                                   grouped  = encoded.grouped(settings.maxPublishingSize).toSeq
                                   results <- ZIO.foreach(grouped)(publish(_, processingHistory))
                                 } yield ()
                               }
                             }
                             .runDrain
                             .fork
    } yield fiber

  /**
   * Stream of snapshots that will back pressure
   * if the snapshot polling starts to exceed the what the processing
   * stream can manage.
   *
   * "check valve" semantics are in place to avoid running out of memory.
   * This is acheived via either a sliding or dropping queue.
   */
  private val bufferedStream = {
    val stream = ZStream
      .tick(settings.pollingInterval)
      // .tap(_ =>
      //   Clock
      //     .currentTime(TimeUnit.MILLISECONDS)
      //     .flatMap(now => Console.printLine(s"::: > Polling snapshot now @ $now.")),
      // )
      // .mapZIO(_ => registry.snapshot)
      .mapZIO(_ => registry.snapshot)
      .map(Chunk.fromIterable)

    val buffered = settings.snapshotQueueType match {
      case Dropping => stream.bufferDropping(settings.snapshotQueueSize)
      case Sliding  => stream.bufferSliding(settings.snapshotQueueSize)
    }

    // TODO Fix it so throttling actually works as expected as the code below isn't working as expected.
    settings.throttling.foldLeft(buffered) { (stream, throttling) =>
      stream.throttleShape(throttling.throttlingSize, throttling.throttlingWindow)(chunks => chunks.flatten.size.toLong)
    }
  }

  private def encode(
    processingHistory: Ref[Map[MetricKey.Untyped, Long]],
    snapshot: Iterable[MetricPair.Untyped],
  ) = {

    val filtered = ZIO.foreach(snapshot)(withTimestamps(processingHistory, _)).map(_.filter(filterOnTimestamps _))
    filtered.flatMap {
      case wts if wts.nonEmpty =>
        for {
          // _       <- Console.printLine(s"::: > Encoding chunk of size ${wts.size}.")
          encoded <- ZIO.foreachPar(wts)(tup =>
                       Clock.instant.flatMap {
                         now => // TODO: Optimize to only call Clock.instant if both of the timestamps are `None`.
                           val timestamp = tup._3 orElse tup._2 getOrElse now.toEpochMilli
                           encoder.encodeMetric(tup._1, timestamp).map(_.map(a => (a, tup._1.metricKey, timestamp)))
                       },
                     )

        } yield encoded.flatten
      case _ => ZIO.succeed(Seq.empty)  
    }
  }

  private def filterOnTimestamps(tup: (MetricPair.Untyped, Option[Long], Option[Long])) = {
    val passed = tup match {
      case (_, Some(lastKnownTs), Some(currentTs)) => currentTs > lastKnownTs
      case (_, None, Some(_)) | (_, Some(_), None) => true
      case (_, None, None)                         => true
      case _                                       => false
    }
    passed
  }

  private def publish(
    incoming: Iterable[(A, MetricKey.Untyped, Long)],
    processingHistory: Ref[Map[MetricKey.Untyped, Long]],
  ) =
    for {
      // _      <- Console.printLine(s"::: > Publishing chunk of size ${incoming.size}.")
      result <- publisher.publish(incoming.map(_._1))
      _      <- ZIO.foreachPar(incoming)(tup => processingHistory.update(_ + (tup._2 -> tup._3)))

    } yield result

  private def withTimestamps(
    processingHistory: Ref[Map[MetricKey.Untyped, Long]],
    pair: MetricPair.Untyped,
  ) = {
    val key = pair.metricKey
    for {
      lastProcessedTs <- processingHistory.get.map(_.get(key))
      currentTs       <- registry.lastProcessingTime(key)
    } yield (pair, lastProcessedTs, currentTs)
  }

}
