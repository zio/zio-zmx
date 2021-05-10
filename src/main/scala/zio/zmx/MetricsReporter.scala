package zio.zmx

import zio._
import zio.clock._
import zio.zmx.metrics.MetricEvent
import zio.zmx.state.MetricState
import zio.zmx.statsd.{ StatsdConfig, StatsdReporter }

/**
 * The `MetricsReporter` service is responsible for handling the reporting of
 * all metrics collected in an application.
 */
// TODO: Delete interface and this a layer that is executed solely for its acquire and release
trait MetricsReporter {
  def report(event: MetricEvent): UIO[Any]
  def snapshot: UIO[Map[String, MetricState]] // TODO: Delete in favor of method in package object
}

object MetricsReporter {

  def report(event: MetricEvent): ZIO[Has[MetricsReporter], Nothing, Any] =
    ZIO.accessM(_.get.report(event))

  val none: ZLayer[Any, Nothing, Any] =
    ZLayer.succeed {
      new MetricsReporter {
        def report(event: MetricEvent): UIO[Any]    =
          ZIO.unit
        val snapshot: UIO[Map[String, MetricState]] =
          UIO.succeedNow(Map.empty)
      }
    }

  val statsd: ZLayer[Has[StatsdConfig], Nothing, Has[MetricsReporter]] =
    (for {
      config <- ZIO.service[StatsdConfig]
      gauges <- Ref.make[Map[String, Double]](Map.empty)
    } yield StatsdReporter(config, gauges)).toLayer
}
