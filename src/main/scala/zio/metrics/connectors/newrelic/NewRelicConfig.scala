package zio.metrics.connectors.newrelic

import java.time.Duration

import zio._
import zio.metrics.connectors.EnvVar

final case class NewRelicConfig(
  apiKey: String,
  newRelicURI: String,
  maxMetricsPerRequest: Int,
  maxPublishingDelay: Duration
)

object NewRelicConfig {

  val NAURI = "https://metric-api.newrelic.com/metric/v1"
  val EUURI = "https://metric-api.eu.newrelic.com/metric/v1"

  object Settings {
    object envvars {

      val apiKey               = EnvVar.string("NEW_RELIC_API_KEY", "NewRelicPublisher#Settings")
      val metricsUri           = EnvVar.string("NEW_RELIC_URI", "NewRelicPublisher#Settings")
      val maxMetricsPerRequest = EnvVar.int("NEW_RELIC_MAX_METRICS_PER_REQUEST", "NewRelicPublisher#Settings")
      val maxPublishingDelay   = EnvVar.duration("NEW_RELIC_MAX_PUBLISHING_DELAY", "NewRelicPublisher#Settings")

    }

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
    def live = ZLayer
      .fromZIO(for {
        apiKey               <- envvars.apiKey.get
        newRelicUri          <- envvars.metricsUri.getWithDefault(NAURI)
        maxMetricsPerRequest <- envvars.maxMetricsPerRequest.getWithDefault(1000)
        maxPublishingDelay   <- envvars.maxPublishingDelay.getWithDefault(
                                  5.seconds,
                                ) // TODO: This probably needs to be more like a minute for the default.

      } yield (NewRelicConfig(apiKey, newRelicUri, maxMetricsPerRequest, maxPublishingDelay)))
      .orDie

    /**
     * Attempts to load the Settings from the environment.
     *
     * ===Environment Variables===
     *
     *  - '''`NEW_RELIC_API_KEY`''': Your New Relic API Key.  '''Required'''.
     *  - '''`NEW_RELIC_URI`''':     The New Relic Metric API URI.  '''Optional'''.  Defaults to `https://metric-api.eu.newrelic.com/metric/v1`.
     *
     * REF: [[https://docs.newrelic.com/docs/accounts/accounts-billing/account-setup/choose-your-data-center/#endpoints New Relic's Accounts Doc]]
     */
    def liveEU = ZLayer
      .fromZIO(for {
        apiKey               <- envvars.apiKey.get
        newRelicUri          <- envvars.metricsUri.getWithDefault(EUURI)
        maxMetricsPerRequest <- envvars.maxMetricsPerRequest.getWithDefault(1000)
        maxPublishingDelay   <- envvars.maxPublishingDelay.getWithDefault(
                                  5.seconds,
                                ) // TODO: This probably needs to be more like a minute for the default.

      } yield (NewRelicConfig(apiKey, newRelicUri, maxMetricsPerRequest, maxPublishingDelay)))
      .orDie
  }
}

