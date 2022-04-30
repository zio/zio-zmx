package zio.zmx.newrelic
/*
 * Copyright 2022 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import zio._
import zio.json.ast._
import zio.stream.ZStream
import zio.zmx._

import NewRelicPublisher._
import zhttp.http._
import zhttp.service._

final case class NewRelicPublisher(
  channelFactory: ChannelFactory,
  eventLoopGroup: EventLoopGroup,
  settings: Settings,
  publishingQueue: Queue[Json])
    extends MetricPublisher[Json] {

  val headers = Headers.apply(
    "Api-Key"      -> settings.apiKey,
    "Content-Type" -> "application/json",
    "Accept"       -> "*/*",
  )

  val env = ZEnvironment(channelFactory, eventLoopGroup, settings)

  private def send(json: Iterable[Json]) =
    if (json.nonEmpty) {
      val body = Json
        .Arr(
          Json.Obj("metrics" -> Json.Arr(json.toSeq: _*)),
        )
        .toString

      val request =
        URL.fromString(settings.newRelicURI).map { url =>
          Request(
            method = Method.POST,
            url = url,
            headers = headers,
            data = HttpData.fromString(body),
          )
        }

      val pgm = for {
        // _       <- Console.printLine(body)
        request <- ZIO.fromEither(request)
        result  <- Client.request(request, Client.Config.empty)
        _       <- Console.printLine(s":::> NewRelicPublisher.send, ${json.size} metrics sent.")

      } yield ()

      pgm
        .provideEnvironment(env)
        .map(_ => MetricPublisher.Result.Success)
        .catchAll(e => ZIO.succeed(MetricPublisher.Result.TerminalFailure(e)))
    } else ZIO.succeed(MetricPublisher.Result.Success)

  def run =
    ZStream
      .fromQueue(publishingQueue)
      .groupedWithin(1000, 5.seconds)
      .mapZIO(send)
      .runDrain
      .forkDaemon
      .unit

  def publish(json: Iterable[Json]): ZIO[Any, Nothing, MetricPublisher.Result] =
    publishingQueue.offerAll(json).as(MetricPublisher.Result.Success)
}

object NewRelicPublisher {

  val NAURI = "https://metric-api.newrelic.com/metric/v1"
  val EUURI = "https://metric-api.eu.newrelic.com/metric/v1"

  final case class Settings(
    apiKey: String,
    newRelicURI: String,
    maxMetricsPerRequest: Int,
    maxPublishingDelay: Duration)

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

      } yield (Settings(apiKey, newRelicUri, maxMetricsPerRequest, maxPublishingDelay)))
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

      } yield (Settings(apiKey, newRelicUri, maxMetricsPerRequest, maxPublishingDelay)))
      .orDie
  }

}
