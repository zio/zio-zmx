package zio.metrics.connectors.newrelic

sealed trait NewRelicUri {
  def endpoint: String
}

object NewRelicUri {
  final case class Custom(override val endpoint: String) extends NewRelicUri
  final case object NA                                   extends NewRelicUri { override val endpoint = "https://metric-api.newrelic.com/metric/v1"    }
  final case object EU                                   extends NewRelicUri { override val endpoint = "https://metric-api.eu.newrelic.com/metric/v1" }
}

