package zio.metrics.connectors.prometheus

import zio._

class PrometheusPublisher private (
  current: Ref[String]) {

  def get(implicit trace: Trace): UIO[String] =
    current.get

  def set(next: String)(implicit trace: Trace): UIO[Unit] =
    current.set(next)
}

object PrometheusPublisher {

  def make = for {
    current <- Ref.make[String]("")
  } yield new PrometheusPublisher(current)

}
