package zio.zmx.prometheus

import zio._
import zio.zmx._

class PrometheusPublisher private (
  current: Ref[String],
  next: Ref[Seq[String]])
    extends MetricPublisher[String] {

  override def startSnapshot(implicit trace: Trace): UIO[Unit] =
    next.set(Seq.empty)

  override def completeSnapshot(implicit trace: Trace): UIO[Unit] =
    (next.getAndSet(Seq.empty).map(_.mkString("\n"))).flatMap(current.set)

  override def publish(metrics: Iterable[String]): UIO[MetricPublisher.Result] =
    next.update(_.appendedAll(metrics)).as(MetricPublisher.Result.Success)

  def get(implicit trace: Trace): UIO[String] =
    current.get
}

object PrometheusPublisher {

  def make = for {
    current <- Ref.make[String]("")
    acc     <- Ref.make[Seq[String]](Seq.empty)
  } yield new PrometheusPublisher(current, acc)

  val get: RIO[PrometheusPublisher, String] =
    ZIO.serviceWithZIO[PrometheusPublisher](_.get)
}
