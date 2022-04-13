package zio.zmx

import zio._
import zio.internal.metrics._
import zio.metrics._
import zio.stream._

import izumi.reflect.Tag

trait MetricAgent[A] {

  def runAgent: ZIO[Any, Nothing, Fiber[Throwable, Unit]]
}

object MetricAgent {

  final case class Settings(pollingInterval: Duration, batchMaxSize: Int, batchMaxDelay: Duration)

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
      fiber             <-
        ZStream
          .tick(settings.pollingInterval)
          .mapZIO(_ => registry.snapshot)
          .mapZIO { snapshot =>
            mutex.withPermit {
              ZIO.succeed(processingStream(processingHistory, snapshot))
            }
          }
          .flatten
          .runDrain
          .fork
    } yield fiber

  private def filterOnTimestamps(tup: (MetricPair.Untyped, Option[Long], Option[Long])) = {
    val passed = tup match {
      case (_, Some(lastKnownTs), Some(currentTs)) => currentTs > lastKnownTs
      case (_, None, Some(_)) | (_, Some(_), None) => true
      case (_, None, None)                         => true
      case _                                       => false
    }
    println(s"::: >> `filterOnTimestamps =$passed`: lastKnownTs: ${tup._2}, currentTs: ${tup._3}")

    passed
  }

  private def processingStream(
    processingHistory: Ref[Map[MetricKey.Untyped, Long]],
    snapshot: Set[MetricPair.Untyped],
  ) =
    ZStream
      .fromIterable(snapshot)
      .mapZIO(withTimestamps(processingHistory, _))
      .filter(filterOnTimestamps _)
      .mapConcatChunkZIO(tup =>
        Clock.instant.flatMap {
          now => // TODO: Optimize to only call Clock.instant if both of the timestamps are `None`.
            val timestamp = tup._3 orElse tup._2 getOrElse now.toEpochMilli
            encoder.encodeMetric(tup._1, timestamp).map(_.map(a => (a, tup._1.metricKey, timestamp)))
        },
      )
      .groupedWithin(settings.batchMaxSize, settings.batchMaxDelay)
      .mapConcatChunkZIO { tup =>
        publisher.publish(tup.map(_._1)) *> ZIO.succeed(tup)
      }
      .mapZIO(tup => processingHistory.update(_ + (tup._2 -> tup._3)))

  private def withTimestamps(
    last: Ref[Map[MetricKey.Untyped, Long]],
    pair: MetricPair.Untyped,
  ) = {
    val key = pair.metricKey
    for {
      lastProcessedTs <- last.get.map(_.get(key))
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
