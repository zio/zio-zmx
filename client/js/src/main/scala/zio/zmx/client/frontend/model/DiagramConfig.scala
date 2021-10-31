package zio.zmx.client.frontend.model

import zio._
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
  // The metrics that shall be displayed in the configured diagram
  metric: Chunk[MetricKey],
  // The update interval
  refresh: Duration
)

/**
 * The configuration for all diagrams currently displayed. This shall be maintained
 * in the top level AppState, so that we just can implement a (de)serialization of
 * this class to im/export the entire dashboard
 */
final case class DashBoardConfig(
  // The server url we want to use for the client
  connectUrl: String,
  // The diagrams currently displayed in the dashboard
  diagrams: Chunk[DiagramConfig]
)
