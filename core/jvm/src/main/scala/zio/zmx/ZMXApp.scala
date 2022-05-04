package zio.zmx

import zio._

import izumi.reflect.Tag

/**
 * Base ZIO Application that collects metrics using the [[[zio.zmx.MetricClient]]].
 *
 * Both JVM and ZIO internal metrics collection can be by providing
 *  [[[zio.zmx.InternalMetrics#Settings]]] with the desired flags.
 */
abstract class ZMXApp[R: Tag] extends ZIOAppDefault {

  val clientLayer = settings >+> InternalMetrics.live >+> MetricClient.live

  override val bootstrap = (clientLayer >+> additionalBootstrapping) ++ (clientLayer >+> metricClientBootstrap)

  /**
   * Provides settings to bootstrap the environment that will be tracking and publishing metrics.
   *
   * @return
   */
  def settings: ZLayer[Any, Nothing, R with ZMXApp.Settings]

  /**
   * Provides for defining an additional bootstrap layer that will be added to the `bootstrap` call.
   */
  def additionalBootstrapping: ZLayer[R with ZMXApp.Settings with MetricClient, Nothing, Any]

  private val metricClientBootstrap = ZLayer.fromZIO(
    MetricClient.run,
  )
}

object ZMXApp {

  type SettingsWithBootstrap[R] =
    (ZLayer[Any, Nothing, R with ZMXApp.Settings], ZLayer[R with ZMXApp.Settings with MetricClient, Nothing, Any])

  type Settings = MetricClient.Settings with InternalMetrics.Settings

  object Settings {

    val live = MetricClient.Settings.live ++ InternalMetrics.Settings.live
  }

  abstract class Default[R: Tag](
    override val additionalBootstrapping: ZLayer[R with ZMXApp.Settings with MetricClient, Nothing, Any],
    override val settings: ZLayer[Any, Nothing, R with ZMXApp.Settings])
      extends ZMXApp[R]

}
