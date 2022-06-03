package zio.metrics.connectors.newrelic

import zio._
import zio.json.ast.Json
import zio.stream._

import zhttp.http._
import zhttp.service._

trait NewRelicClient {
  private[newrelic] def send(data: Chunk[Json]): UIO[Unit]
}

object NewRelicClient {

  private[newrelic] def make: ZIO[NewRelicConfig, Nothing, NewRelicClient] = for {
    cfg <- ZIO.service[NewRelicConfig]
    q   <- Queue.bounded[Json](cfg.maxMetricsPerRequest * 2)
    clt  = new NewRelicClientImpl(cfg, q) {}
    _   <- clt.run
  } yield clt

  sealed abstract private class NewRelicClientImpl(
    cfg: NewRelicConfig,
    publishingQueue: Queue[Json],
  )(implicit trace: Trace)
      extends NewRelicClient {

    private val env =
      ChannelFactory.nio ++ EventLoopGroup.nio()

    override private[newrelic] def send(json: Chunk[Json]) =
      publishingQueue.offerAll(json).unit

    private def sendHttp(json: Chunk[Json]) =
      if (json.nonEmpty) {
        val body = Json
          .Arr(
            Json.Obj("metrics" -> Json.Arr(json.toSeq: _*)),
          )
          .toString

        val request =
          URL.fromString(cfg.newRelicURI.endpoint).map { url =>
            Request(
              method = Method.POST,
              url = url,
              headers = headers,
              data = HttpData.fromString(body),
            )
          }

        val pgm = for {
          request <- ZIO.fromEither(request)
          result  <- Client.request(request, Client.Config.empty)
        } yield ()

        pgm.provide(env)
      } else ZIO.unit

    private[newrelic] def run =
      ZStream
        .fromQueue(publishingQueue)
        .groupedWithin(cfg.maxMetricsPerRequest, cfg.maxPublishingDelay)
        .mapZIO(sendHttp)
        .runDrain
        .forkDaemon
        .unit

    private lazy val headers = Headers.apply(
      "Api-Key"      -> cfg.apiKey,
      "Content-Type" -> "application/json",
      "Accept"       -> "*/*",
    )
  }
}
