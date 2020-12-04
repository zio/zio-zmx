package zio.zmx

import zio._
import zio.clock._
import zio.zmx.MetricsDataModel._
import zio.zmx.MetricsConfigDataModel._

// TODO: Move statsd specific stuff in here
package object statsd {

  /**
   * Constructs a live `Metrics` service based on the given configuration.
   */
  def live(config: MetricsConfig): RLayer[Clock, Metrics] =
    ZLayer.identity[Clock] ++ zio.zmx.statsd.StatsdClient.live(config) >>>
      ZLayer.fromServicesM[Clock.Service, zio.zmx.statsd.StatsdClient.Service, Any, Throwable, Metrics.Service] {
        (clock, statsdClient) =>
          for {
            aggregator <- Ref.make[Chunk[Metric[_]]](Chunk.empty)
          } yield new zio.zmx.statsd.Live(config, clock, statsdClient, aggregator)
      }

}
