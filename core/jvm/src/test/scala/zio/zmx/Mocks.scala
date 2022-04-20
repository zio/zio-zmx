package zio.zmx

import zio._
import zio.metrics._

object Mocks {

  final case class MockMetricEncoder[A](
    recording: Ref[Chunk[(MetricPair.Untyped, Long)]],
    encodedOutput: (MetricPair.Untyped, Long) => Chunk[A])
      extends MetricEncoder[A] {

    override def encodeMetric(metric: MetricPair.Untyped, timestamp: Long): ZIO[Any, Throwable, Chunk[A]] =
      recording.modify { chunk =>
        val update = chunk :+ (metric -> timestamp)
        val output = encodedOutput(metric, timestamp)
        (output, update)
      }

    def state: UIO[Chunk[(MetricPair.Untyped, Long)]] = recording.get
  }

  object MockMetricEncoder {
    def mock[A: Tag](encodedOutput: (MetricPair.Untyped, Long) => Chunk[A]) =
      ZLayer.fromZIO(
        Ref.make(Chunk.empty[(MetricPair.Untyped, Long)]).map(MockMetricEncoder[A](_, encodedOutput)),
      )
  }

  final case class MockMetricPublisher[A](private val recording: Ref[Chunk[Chunk[A]]]) extends MetricPublisher[A] {
    override def publish(json: Iterable[A]): ZIO[Any, Nothing, MetricPublisher.Result] =
      recording.update(_ :+ Chunk.fromIterable(json)) *> ZIO.succeed(MetricPublisher.Result.Success)

    def state = recording.get
  }

  object MockMetricPublisher {
    def mock[A: Tag] = ZLayer.fromZIO(
      Ref.make(Chunk.empty[Chunk[A]]).map(new MockMetricPublisher[A](_)),
    )
  }

  final case class MockMetricRegistry private (
    recording1: Ref[Chunk[MetricKey.Untyped]],
    recording2: Ref[Chunk[(MetricKey.Untyped, Long)]],
    timestamps: Ref[Map[MetricKey.Untyped, (MetricPair.Untyped, Long)]])
      extends MetricRegistry {

    override def lastProcessingTime(key: MetricKey.Untyped): ZIO[Any, Throwable, Option[Long]] =
      recording1.update(_ :+ key) *> timestamps.get.map(_.get(key).map(_._2))

    override def snapshot: ZIO[Any, Throwable, Set[MetricPair.Untyped]] =
      timestamps.get.map(timestamps => Set.from(timestamps.values.map(_._1)))

    override def updateProcessingTime(key: MetricKey.Untyped, value: Long): ZIO[Any, Throwable, Unit] =
      recording2.update(_ :+ (key -> value))

    def putMetric(tup: (MetricPair.Untyped, Long)*) =
      timestamps.update { timestamps =>
        tup.foldLeft(timestamps) { case (acc, (pair, timestamp)) =>
          acc + (pair.metricKey -> (pair -> timestamp))
        }
      }

    val state = for {
      s1 <- recording1.get
      s2 <- recording2.get
    } yield (s1, s2)
  }

  object MockMetricRegistry {
    def mock(timestamps: Map[MetricKey.Untyped, (MetricPair.Untyped, Long)] = Map.empty) =
      ZLayer.fromZIO(for {
        r1 <- Ref.make(Chunk.empty[MetricKey.Untyped])
        r2 <- Ref.make(Chunk.empty[(MetricKey.Untyped, Long)])
        ts <- Ref.make(Map.empty[MetricKey.Untyped, (MetricPair.Untyped, Long)])
      } yield MockMetricRegistry(r1, r2, ts))
  }

}
