package zio.metrics.connectors

import zio._

final case class CollectingPublisher[A](
  encoded: Ref[Chunk[A]])
    extends MetricPublisher[A] {

  override def publish(metrics: Iterable[A]): UIO[MetricPublisher.Result] =
    encoded.update(_.appendedAll(metrics)).as(MetricPublisher.Result.Success)

  def get = encoded.get
}
