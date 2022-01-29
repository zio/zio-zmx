package zio.zmx.client.frontend.model

import upickle.default._
import zio._
import zio.metrics._
import zio.zmx.client.UPickleCoreImplicits

import java.time.Duration
import java.util.UUID.randomUUID
import scalajs.js

sealed trait PanelConfig {
  def id: String
  def title: String
}

object PanelConfig {

  /**
   * An empty panel is not yet configured and will be used whenever a new panel is inserted into the Dashboard
   */
  final case class EmptyConfig(
    id: String,
    title: String
  ) extends PanelConfig

  object EmptyConfig {

    def create(title: String): EmptyConfig =
      EmptyConfig(s"$randomUUID", title)

    implicit lazy val rwEmptyConfig: ReadWriter[EmptyConfig] = macroRW
  }

  sealed trait DisplayType
  object DisplayType {
    case object Diagram extends DisplayType
    case object Summary extends DisplayType

    implicit lazy val rwDisplayType: ReadWriter[DisplayType] = macroRW
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
    metrics: Chunk[MetricKey],
    // The update interval
    refresh: Duration,
    // how many data points shall we keep for each metric in this diagram
    maxSamples: Int,
    // An optional Vega-JSON that shall be used to render the graph
    vegaConfig: Option[js.Dynamic]
  ) extends PanelConfig

  object DisplayConfig {
    import UPickleCoreImplicits._

    implicit val rwJsDynamic: ReadWriter[js.Dynamic] =
      readwriter[String].bimap(
        js.JSON.stringify(_),
        js.JSON.parse(_)
      )

    implicit val rwDisplayConfig: ReadWriter[DisplayConfig] = macroRW
  }

  implicit val rwPanelConfig: ReadWriter[PanelConfig] = macroRW
}
