package zio.zmx.client.frontend.model

import zio._
import zio.metrics._
import java.time.Duration
import java.util.UUID

import zio.zmx.client.frontend.utils.Implicits._

sealed trait PanelConfig {
  def id: String
  def title: String
}

object PanelConfig {

  final case class EmptyPanel(
    id: String,
    title: String
  ) extends PanelConfig

  object EmptyPanel {
    def create(title: String): EmptyPanel = EmptyPanel(UUID.randomUUID().toString(), title)
  }

  final case class SummaryConfig(
    id: String,
    title: String,
    metrics: Chunk[MetricKey]
  ) extends PanelConfig {
    def toDiagramConfig: PanelConfig = DiagramConfig(
      id,
      title,
      metrics,
      Duration.ofSeconds(5)
    )
  }

  final case class DiagramConfig(
    // Unique ID
    id: String,
    // The diagram title
    title: String,
    // The metrics that shall be displayed in the configured diagram
    metrics: Chunk[MetricKey],
    // The update interval
    refresh: Duration
  ) extends PanelConfig {
    def toSummaryConfig: PanelConfig = SummaryConfig(id, title, metrics)
  }

  object DiagramConfig {
    def fromMetricKey(k: MetricKey) =
      DiagramConfig(
        UUID.randomUUID().toString,
        s"A diagram view for: ${k.longName}",
        Chunk(k),
        Duration.ofSeconds(5)
      )
  }
}
