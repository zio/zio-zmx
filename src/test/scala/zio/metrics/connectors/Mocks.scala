package zio.metrics.connectors

import zio._

object Mocks {

  final case class MockMetricEncoder[A](
    recording: Ref[Chunk[MetricEvent]],
    encodedOutput: MetricEvent => Chunk[A])
      extends MetricEncoder[A] {

    override def encode(event: MetricEvent): ZIO[Any, Throwable, Chunk[A]] =
      recording.modify { chunk =>
        val update = chunk :+ event
        val output = encodedOutput(event)
        (output, update)
      }

    def state: UIO[Chunk[MetricEvent]] = recording.get
  }

  object MockMetricEncoder {
    def mock[A: Tag](encodedOutput: MetricEvent => Chunk[A]) =
      ZLayer.fromZIO(
        Ref.make(Chunk.empty[MetricEvent]).map(MockMetricEncoder[A](_, encodedOutput)),
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

}
