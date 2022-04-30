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
  eventLoopGroop: EventLoopGroup,
  settings: Settings,
  publishingQueue: Queue[Json])
    extends MetricPublisher[Json] {

  val headers = Headers.apply(
    "Api-Key"      -> settings.apiKey,
    "Content-Type" -> "application/json",
    "Accept"       -> "*/*",
  )

  val env = ZLayer.succeed(channelFactory) ++ ZLayer.succeed(eventLoopGroop) ++ ZLayer.succeed(settings)

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
        .provide(env)
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

  final case class Settings(apiKey: String, newRelicURI: String)

  object Settings {
    object envvars {

      val apiKey     = EnvVar.string("NEW_RELIC_API_KEY", "NewRelicPublisher#Settings")
      val metricsUri = EnvVar.string("NEW_RELIC_URI", "NewRelicPublisher#Settings")
    }

    /**
     * Attempts to load the Settings from the environment.
     *
     * ===Environment Variables===
     *
     *  - '''`NEW_RELIC_API_KEY`''': Your New Relic API Key.  '''Required'''.
     *  - '''`NEW_RELIC_URI`''':     The New Relic Metric API URI.  '''Optional'''.  Defaults to `https://metric-api.newrelic.com/metric/v1`.
     */
    def live = ZLayer.fromZIO(for {
      apiKey      <- envvars.apiKey.get
      newRelicUri <- envvars.metricsUri.getWithDefault(NAURI)

    } yield (Settings(apiKey, newRelicUri))).orDie

    /**
     * Uses the NA datacenter endpoint defined here: [[https://docs.newrelic.com/docs/data-apis/ingest-apis/metric-api/report-metrics-metric-api/#api-endpoint New Relic's Metric API Doc]]
     *
     * @param apiKey
     * @return
     */
    def makeNA(apiKey: String) = Settings(apiKey, NAURI)

    /**
     * Uses the EU datacenter endpoint defined here: [[https://docs.newrelic.com/docs/accounts/accounts-billing/account-setup/choose-your-data-center/#endpoints New Relic's Accounts Doc]]
     *
     * @param apiKey
     * @return
     */
    def makeEU(apiKey: String) = Settings(apiKey, "https://metric-api.eu.newrelic.com/metric/v1")

    val forNA = ZLayer.fromZIO(
      envvars.apiKey.get.orDie
        .map(makeNA),
    )

    val forEU = ZLayer.fromZIO(
      envvars.apiKey.get.orDie
        .map(makeEU),
    )
  }

}
