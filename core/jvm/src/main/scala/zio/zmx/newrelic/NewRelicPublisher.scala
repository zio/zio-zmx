package zio.zmx.newrelic

import zio._
import zio.json.ast._
import zio.zmx.MetricPublisher

import NewRelicPublisher._
import zhttp.http._
import zhttp.service._

// trait NewRelicClient {

// val env = ChannelFactory.auto ++ EventLoopGroup.auto()
// val url = "http://sports.api.decathlon.com/groups/water-aerobics"

// val program = for {
//   res  <- Client.request(url)
//   data <- res.bodyAsString
//   _    <- Console.printLine(data)
// } yield ()

//   ???

// }
// def sendMetrics(json: Chunk[Json]): ZIO[Any, Throwable, Unit]
// }

final case class NewRelicPublisher(channelFactory: ChannelFactory, eventLoopGroop: EventLoopGroup, settings: Settings)
    extends MetricPublisher[Json] {
  def publish(json: Iterable[Json]): ZIO[Any, Nothing, MetricPublisher.Result] = {

    val body = Json
      .Arr(
        Json.Obj("metrics" -> Json.Arr(json.toSeq: _*)),
      )
      .toString

    val headers = Headers.apply(
      "Api-Key"      -> settings.apiKey,
      "Content-Type" -> "application/json",
      "Accept"       -> "*/*",
    )

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
      request <- ZIO.fromEither(request)
      result  <- Client.request(request, Client.Config.empty)

    } yield ()

    val layer = ZLayer.succeed(channelFactory) ++ ZLayer.succeed(eventLoopGroop)

    pgm
      .provide(layer)
      .map(_ => MetricPublisher.Result.Success)
      .catchAll(e => ZIO.succeed(MetricPublisher.Result.TerminalFailure(e)))
  }
}

object NewRelicPublisher {

  final case class Settings(apiKey: String, newRelicURI: String)

}
