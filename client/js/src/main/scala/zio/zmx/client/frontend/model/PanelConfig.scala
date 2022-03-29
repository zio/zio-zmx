package zio.zmx.client.frontend.model

import java.time.Duration
import java.util.UUID.randomUUID

import zio.json._
import zio.metrics._
import zio.zmx.client.MetricsMessageImplicits

import scalajs.js

sealed trait PanelConfig {
  def id: String
  def title: String
}

object PanelConfig {

  implicit lazy val encPanelConfig: JsonEncoder[PanelConfig] =
    DeriveJsonEncoder.gen[PanelConfig]
  implicit lazy val decPanelConfig: JsonDecoder[PanelConfig] =
    DeriveJsonDecoder.gen[PanelConfig]

  /**
   * An empty panel is not yet configured and will be used whenever a new panel is inserted into the Dashboard
   */
  final case class EmptyConfig(
    id: String,
    title: String)
      extends PanelConfig

  object EmptyConfig {
    def create(title: String): EmptyConfig =
      EmptyConfig(s"$randomUUID", title)
  }

  sealed trait DisplayType
  object DisplayType {
    case object Diagram extends DisplayType
    case object Summary extends DisplayType

    implicit lazy val encDisplayType: JsonEncoder[DisplayType] =
      DeriveJsonEncoder.gen[DisplayType]
    implicit lazy val decDisplayType: JsonDecoder[DisplayType] =
      DeriveJsonDecoder.gen[DisplayType]
  }

  /**
   * A diagram config is used to configure a panel showing the chart of zero or more metrics.
   */
  final case class DisplayConfig(
    // Unique ID
    id: String,
    // How should the content be displayed
    display: DisplayType,
    // The diagram title
    title: String,
    // The metrics that shall be displayed in the configured diagram
    metrics: Set[MetricKey.Untyped],
    // The update interval
    refresh: Duration,
    // how many data points shall we keep for each metric in this diagram
    maxSamples: Int,
    // An optional Vega-JSON that shall be used to render the graph
    vegaConfig: Option[js.Dynamic])
      extends PanelConfig

  object DisplayConfig {
    import MetricsMessageImplicits._

    implicit lazy val encJsDynamic: JsonEncoder[js.Dynamic] =
      JsonEncoder[String].contramap[js.Dynamic](dyn => js.JSON.stringify(dyn))
    implicit val decJsDynamic: JsonDecoder[js.Dynamic]      =
      JsonDecoder[String].map(s => js.JSON.parse(s))

    implicit lazy val encDisplayConfig: JsonEncoder[DisplayConfig] =
      DeriveJsonEncoder.gen[DisplayConfig]
    implicit lazy val decDisplayConfig: JsonDecoder[DisplayConfig] =
      DeriveJsonDecoder.gen[DisplayConfig]
  }
}
