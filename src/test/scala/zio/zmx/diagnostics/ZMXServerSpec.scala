package zio.zmx.diagnostics

import zio.ZIO
import zio.test.{ testM, _ }
import zio.duration._
import zio.test.TestAspect.{ sequential, timeout }
import zio.zmx.diagnostics.fibers.FiberDumpProvider
import zio.zmx._
import zio.zmx.diagnostics.parser.ZMXParser

object ZMXServerSpec extends DefaultRunnableSpec {

  def spec =
    suite("ZMXServerSpec")(
      testM("Is properly opened and closed") {
        val zio = for {
          _ <- openAndCloseServer
        } yield assertCompletes
        zio.provideCustomLayer(ZMXParser.respParser ++ FiberDumpProvider.live(ZMXSupervisor))
      } @@ timeout(5.seconds),
      testM("Is properly reopened twice") {
        val zio = for {
          _ <- openAndCloseServer
          _ <- openAndCloseServer
        } yield assertCompletes
        zio.provideCustomLayer(ZMXParser.respParser ++ FiberDumpProvider.live(ZMXSupervisor))
      } @@ timeout(5.seconds)
    ) @@ sequential

  val server             = ZMXServer.make(ZMXConfig("localhost", 1111, true))
  val openAndCloseServer = server.use(_ => ZIO.unit)
}
