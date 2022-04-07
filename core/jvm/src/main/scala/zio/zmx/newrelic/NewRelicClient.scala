package zio.zmx.newrelic

import zio._
import zio.json.ast._
trait NewRelicClient {

  // val env = ChannelFactory.auto ++ EventLoopGroup.auto()
  // val url = "http://sports.api.decathlon.com/groups/water-aerobics"

  // val program = for {
  //   res  <- Client.request(url)
  //   data <- res.bodyAsString
  //   _    <- Console.printLine(data)
  // } yield ()

  //   ???

  // }
  def sendMetrics(json: Chunk[Json]): ZIO[Any, Throwable, Unit] 
}
