package zio.zmx.client.frontend.state

import org.scalajs.dom.ext.Color
import zio.zmx.internal.MetricKey

/**
 * The configuration for a single time series graph within a diagram.
 * A single time series is either coming from a counter, a gauge or represents
 * a single element of a histogram, summary or set. For the latter a subkey
 * must be defined, which element is relevant for the time series.
 */
final case class TimeSeriesConfig(
  key: MetricKey,
  subKey: Option[String],
  color: Color,
  tension: Double
)

/**
 * The configuration for a single diagram currently displayed
 */
final case class DiagramConfig(
  // The diagram title
  title: String
)

/**
 * The configuration for all diagrams currently displayed
 */
final case class DashBoard(
)
