package zio.metrics.connectors.prometheus

import zio._

private[prometheus] class PrometheusPublisher(
  current: Ref[String]) {

  def get(implicit trace: Trace): UIO[String] =
    current.get

  def set(next: String)(implicit trace: Trace): UIO[Unit] =
    current.set(next)
}

private[prometheus] object PrometheusPublisher {

  def make = for {
    current <- Ref.make[String]("")
  } yield new PrometheusPublisher(current)

}
