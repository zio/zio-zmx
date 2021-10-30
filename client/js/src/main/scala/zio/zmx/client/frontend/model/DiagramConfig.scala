package zio.zmx.client.frontend.model

import zio.metrics._

/**
 * The configuration for a single diagram currently displayed
 */
final case class DiagramConfig(
  // The diagram title
  title: String,
  // the metric displayed in the diagram
  metric: MetricKey
)

/**
 * The configuration for all diagrams currently displayed
 */
final case class DashBoard(
)
