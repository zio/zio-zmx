package zio.zmx.client.frontend.model

import zio.metrics._
import java.time.Duration

/**
 * The configuration for a single diagram currently displayed
 */
final case class DiagramConfig(
  // Unique ID
  id: String,
  // The diagram title
  title: String,
  // the metric displayed in the diagram
  metric: MetricKey,
  // The update interval
  refresh: Duration
)

/**
 * The configuration for all diagrams currently displayed
 */
final case class DashBoard(
)
