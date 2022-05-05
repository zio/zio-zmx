package zio.metrics.connectors.newrelic

import zio._
import zio.metrics.connectors._

import zhttp.service._

abstract class NewRelicApp(override val settings: ZLayer[Any, Nothing, NewRelicApp.Settings])
    extends ZMXApp.Default[NewRelicApp.Settings](
      Scope.default >+> ZLayer.fromZIO {
        MetricClient
          .registerNewRelicListener()
          .provideSome[NewRelicPublisher.Settings with Scope with MetricClient](
            NewRelicApp.nioZhttpLayer.extendScope,
            NewRelicApp.newRelicEncAndPub,
            MetricListener.newRelic,
          )
      },
      settings,
    )

object NewRelicApp {

  val nioZhttpLayer = EventLoopGroup.nio() ++ ChannelFactory.nio

  val newRelicEncAndPub = MetricEventEncoder.newRelic ++ MetricPublisher.newRelic

  type Settings = MetricClient.Settings with NewRelicPublisher.Settings with InternalMetrics.Settings

  object Settings {

    val live = NewRelicPublisher.Settings.live ++ ZMXApp.Settings.live

    val liveEU = NewRelicPublisher.Settings.liveEU ++ ZMXApp.Settings.live
  }

  abstract class ForEU extends NewRelicApp(Settings.liveEU)

  abstract class ForNA extends NewRelicApp(Settings.live)

  abstract class Default(settings: ZLayer[Any, Nothing, NewRelicApp.Settings]) extends NewRelicApp(settings)

}
