package zio.zmx.client.frontend.model

import zio._
import zio.metrics._
import java.time.Duration
import java.util.UUID

import zio.zmx.client.frontend.utils.Implicits._

/**
 * The configuration for a single diagram currently displayed
 */
final case class DiagramConfig(
  // Unique ID
  id: String,
  // The diagram title
  title: String,
  // The metrics that shall be displayed in the configured diagram
  metric: Chunk[MetricKey],
  // The update interval
  refresh: Duration,
  // the position at which the diagram is displayed within the dashboard
  displayIndex: Int
)

object DiagramConfig {
  def fromMetricKey(k: MetricKey) =
    DiagramConfig(UUID.randomUUID().toString, s"A diagram view for: ${k.longName}", Chunk(k), Duration.ofSeconds(5), 0)
}
