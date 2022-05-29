package zio.metrics.connectors.newrelic

import java.time.Duration

import zio._

final case class NewRelicConfig(
  apiKey: String,
  newRelicURI: NewRelicUri,
  maxMetricsPerRequest: Int,
  maxPublishingDelay: Duration)

object NewRelicConfig {

  /**
   * Attempts to load the Settings from the environment.
   *
   * ===Environment Variables===
   *
   *  - '''`NEW_RELIC_API_KEY`''': Your New Relic API Key.  '''Required'''.
   *  - '''`NEW_RELIC_URI`''':     The New Relic Metric API URI.  '''Optional'''.  Defaults to `https://metric-api.newrelic.com/metric/v1`.
   *
   * REF: [[https://docs.newrelic.com/docs/data-apis/ingest-apis/metric-api/report-metrics-metric-api/#api-endpoint New Relic's Metric API Doc]]
   */
  // TODO: Use timecode to try and figure out the NR endpoint
  val fromEnvLayer: ZLayer[Any, Nothing, NewRelicConfig] =
    ZLayer
      .fromZIO(for {
        apiKey               <- System.env(envApiKey).someOrFail(new IllegalArgumentException("APIKey is missing for New Relic"))
        newRelicUri          <- System.env(envMetricsUri).map(_.map(NewRelicUri.Custom.apply)).map(_.getOrElse(NewRelicUri.NA))
        maxMetricsPerRequest <- System.envOrElse(envMaxMetricsPerRequest, "500").map(_.toInt)
        maxPublishingDelay   <- System.envOrElse(envMaxPublishingDelay, "PT5S").map(Duration.parse)
      } yield (NewRelicConfig(apiKey, newRelicUri, maxMetricsPerRequest, maxPublishingDelay)))
      .orDie

  val fromEnvEULayer: ZLayer[Any, Nothing, NewRelicConfig] =
    fromEnvLayer.project(_.copy(newRelicURI = NewRelicUri.EU))

  private lazy val envApiKey               = "NEW_RELIC_API_KEY"
  private lazy val envMetricsUri           = "NEW_RELIC_URI"
  private lazy val envMaxMetricsPerRequest = "NEW_RELIC_MAX_METRICS_PER_REQUEST"
  private lazy val envMaxPublishingDelay   = "NEW_RELIC_MAX_PUBLISHING_DELAY"
}
