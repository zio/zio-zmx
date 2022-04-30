package zio.zmx.newrelic

import zio._
import zio.zmx._

import zhttp.service._

trait NewRelicApp extends ZMXApp[NewRelicApp.Settings] {

  override def addtionalEnvironment: ZLayer[NewRelicApp.Settings with MetricClient, Nothing, Any] =
    Scope.default >+> ZLayer.fromZIO {
      MetricClient
        .registerNewRelicListener()
        .provideSome[NewRelicPublisher.Settings with Scope with MetricClient](
          NewRelicApp.nioZhttpLayer.extendScope,
          NewRelicApp.newRelicEncAndPub,
          MetricListener.newRelic,
        )
    }
  override def settings: ZLayer[Any, Nothing, NewRelicApp.Settings]

}

object NewRelicApp {

  val nioZhttpLayer = EventLoopGroup.nio() ++ ChannelFactory.nio

  val newRelicEncAndPub = MetricEventEncoder.newRelic ++ MetricPublisher.newRelic

  type Settings = MetricClient.Settings with NewRelicPublisher.Settings with InternalMetrics.Settings

  object Settings {

    val live = NewRelicPublisher.Settings.live ++ ZMXApp.Settings.live
  }

  abstract class ForEU extends Default(NewRelicPublisher.Settings.forEU ++ ZMXApp.Settings.live)

  abstract class ForNA extends Default()

  abstract class Default(override val settings: ZLayer[Any, Nothing, Settings] = Settings.live) extends NewRelicApp

}
