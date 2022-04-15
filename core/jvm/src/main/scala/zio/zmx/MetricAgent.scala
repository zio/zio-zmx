package zio.zmx

import java.util.concurrent.TimeUnit

import zio._
import zio.metrics._
import zio.stream._
import zio.zmx.MetricAgent.QueueType.Dropping
import zio.zmx.MetricAgent.QueueType.Sliding

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
    (for {
      encoder   <- ZIO.service[MetricEncoder[A]]
      publisher <- ZIO.service[MetricPublisher[A]]
      recording <- ZIO.service[MetricRegistry]
      settings  <- ZIO.service[Settings]
    } yield LiveMetricAgent(encoder, publisher, recording, settings)).toLayer
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
                                 val partitioned = snapshot.toSet.grouped(settings.maxPublishingSize).toSeq
                                 ZIO.foreach(partitioned) { grouped =>
                                   processing(processingHistory, grouped)
                                 }
                               }
                             }
                             .runDrain
                             .fork
    } yield fiber

  // def runAgent2: ZIO[Any, Nothing, Fiber[Throwable, Unit]] =
  //   for {
  //     processingHistory <- Ref.make(Map.empty[MetricKey.Untyped, Long])
  //     mutex             <- Semaphore.make(1)
  //     fiber             <- bufferedStream
  //                            .mapZIO { snapshot =>
  //                              mutex.withPermit {
  //                                processingStream(processingHistory, snapshot).runDrain
  //                              }
  //                            }
  //                            .runDrain
  //                            .fork
  //   } yield fiber

  /**
   * Stream of snapshots that will back pressure
   * if the snapshot polling starts to exceed the what the processing
   * stream can manage.
   *
   * "release valve" semantics are in place to avoid running out of memory.
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

    // .flattenChunks
    // .grouped(settings.batchMaxSize)

    val buffered = settings.snapshotQueueType match {
      case Dropping => stream.bufferDropping(settings.snapshotQueueSize)
      case Sliding  => stream.bufferSliding(settings.snapshotQueueSize)
    }

    settings.throttling.foldLeft(buffered) { (stream, throttling) =>
      stream.throttleShape(throttling.throttlingSize, throttling.throttlingWindow)(chunks => chunks.flatten.size.toLong)
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

  // private def processingStream(
  //   processingHistory: Ref[Map[MetricKey.Untyped, Long]],
  //   snapshot: Set[MetricPair.Untyped],
  // ) =
  //   ZStream
  //     .fromIterable(snapshot)
  //     .mapZIO(withTimestamps(processingHistory, _))
  //     .filter(filterOnTimestamps _)
  //     // .mapConcatChunkZIO(tup =>
  //     .mapZIO(tup =>
  //       Clock.instant.flatMap {
  //         now => // TODO: Optimize to only call Clock.instant if both of the timestamps are `None`.
  //           val timestamp = tup._3 orElse tup._2 getOrElse now.toEpochMilli
  //           encoder.encodeMetric(tup._1, timestamp).map(_.map(a => (a, tup._1.metricKey, timestamp)))
  //       },
  //     )
  //     // .groupedWithin(settings.batchMaxSize, settings.batchMaxDelay)
  //     .tap(chunk =>
  //       Clock.currentTime(TimeUnit.MILLISECONDS).flatMap { now =>
  //         Console.printLine(
  //           s"::: [$now]> Processing chunk of size ${chunk.size}. 'maxBatchDelay' = ${settings.batchMaxDelay}, 'maxBatchSize' = '${settings.batchMaxSize}'.",
  //         )
  //       },
  //     )
  //     .filter(_.nonEmpty)
  //     .mapConcatChunkZIO { tup =>
  //       publisher.publish(tup.map(_._1)) *> ZIO.succeed(tup)
  //     }
  //     .mapZIO(tup => processingHistory.update(_ + (tup._2 -> tup._3)))

  private def processing(
    processingHistory: Ref[Map[MetricKey.Untyped, Long]],
    snapshot: Iterable[MetricPair.Untyped],
  ) = {

    val filtered = ZIO.foreach(snapshot)(withTimestamps(processingHistory, _)).map(_.filter(filterOnTimestamps _))
    filtered.flatMap {
      case wts if wts.nonEmpty =>
        for {
          // _        <- Console.printLine(s"::: > Processing chunk of size ${wts.size}.")
          encoded  <- ZIO.foreachPar(wts)(tup =>
                        Clock.instant.flatMap {
                          now => // TODO: Optimize to only call Clock.instant if both of the timestamps are `None`.
                            val timestamp = tup._3 orElse tup._2 getOrElse now.toEpochMilli
                            encoder.encodeMetric(tup._1, timestamp).map(_.map(a => (a, tup._1.metricKey, timestamp)))
                        },
                      )
          flattened = encoded.flatten
          result   <- publisher.publish(flattened.map(_._1))
          _        <- ZIO.foreachPar(flattened)(tup => processingHistory.update(_ + (tup._2 -> tup._3)))

        } yield result
      case _                   => ZIO.succeed(MetricPublisher.Result.Success)
    }
  }

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

  // This version has serious race condition if the polling rate is higher than the batch rate.
  // def runAgent: ZIO[Any, Nothing, Fiber[Throwable, Unit]] =
  //   for {
  //     last  <- Ref.make(Map.empty[MetricKey.Untyped, Long])
  //     fiber <-
  //       ZStream
  //         .tick(settings.pollingInterval)
  //         // .tap(a => Console.printLine(s"::: > Step 0. tick: $a"))
  //         // .tap(_ => registry.snapshot.flatMap(a => Console.printLine(s"::: > Step 1. snapshots $a")))
  //         .mapConcatZIO(_ => registry.snapshot)
  //         .mapZIO(withTimestamps(last, _))
  //         .tap(a => Console.printLine(s"::: > Step 1. $a"))
  //         .filter(filterOnTimestamps _)
  //         .mapConcatChunkZIO(tup =>
  //           Clock.instant.flatMap {
  //             now => // TODO: Optimize to only call Clock.instant if both of the timestamps are `None`.
  //               val timestamp = tup._3 orElse tup._2 getOrElse now.toEpochMilli
  //               encoder.encodeMetric(tup._1, timestamp).map(_.map(a => (a, tup._1.metricKey, timestamp)))
  //           }
  //         )
  //         .tap(a => Console.printLine(s"::: >>> Publishing starting"))
  //         .groupedWithin(settings.batchMaxSize, settings.batchMaxDelay)
  //         .tap(a => Console.printLine(s"::: >>>>> Publishing finished.  Chunks: ${a.size}"))
  //         .mapConcatChunkZIO { tup =>
  //           publisher.publish(tup.map(_._1)) *> ZIO.succeed(tup)
  //         }
  //         .mapZIO(tup => Console.printLine(s"::: >>>>>> Updating") *>  last.update(_ + (tup._2 -> tup._3)) *> Console.printLine(s"::: >>>>>>>> Updated"))
  //         .runDrain
  //         .fork
  //   } yield fiber

}
