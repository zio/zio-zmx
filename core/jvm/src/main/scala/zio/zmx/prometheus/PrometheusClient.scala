package zio.zmx.prometheus

import zio._

trait PrometheusClient {
  def update(state: String)(implicit trace: ZTraceElement): UIO[Unit]
  def snapshot(implicit trace: ZTraceElement): UIO[String]
}

object PrometheusClient {

  private[prometheus] class DefaultPrometheusClient(
    state: Ref[String])
      extends PrometheusClient {
    def snapshot(implicit trace: ZTraceElement): UIO[String] = state.get

    def update(newState: String)(implicit trace: ZTraceElement): UIO[Unit] = state.set(newState)
  }

  val live: ZLayer[Any, Nothing, PrometheusClient] =
    ZLayer.fromZIO(
      for {
        state <- Ref.make("")
      } yield new DefaultPrometheusClient(state),
    )

  val snapshot: ZIO[PrometheusClient, Nothing, String] =
    ZIO.serviceWithZIO[PrometheusClient](_.snapshot)
}
