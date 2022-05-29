package zio.metrics.connectors

import java.time.Duration

final case class MetricsConfig(
  interval: Duration)
