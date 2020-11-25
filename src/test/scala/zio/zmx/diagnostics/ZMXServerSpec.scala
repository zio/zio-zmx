package zio.zmx.diagnostics

import zio.ZIO
import zio.test.{ testM, _ }
import zio.duration._
import zio.test.TestAspect.{ nonFlaky, sequential, timeout }

object ZMXServerSpec extends DefaultRunnableSpec {

  def spec =
    suite("ZMXServerSpec")(
      testM("Is properly opened and closed") {
        for {
          _ <- openAndCloseServer
        } yield assertCompletes
      } @@ timeout(5.seconds),
      testM("Is properly reopened twice") {
        for {
          _ <- openAndCloseServer
          _ <- openAndCloseServer
        } yield assertCompletes
      } @@ timeout(5.seconds)
    ) @@ sequential @@ nonFlaky

  val server             = ZMXServer.make(ZMXConfig("localhost", 1111, true))
  val openAndCloseServer = server.use(_ => ZIO.unit)
}
