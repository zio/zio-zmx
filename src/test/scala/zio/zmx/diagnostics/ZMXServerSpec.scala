package zio.zmx.diagnostics

import zio.{Chunk, ZIO}
import zio.test.Assertion.{containsString, equalTo}
import zio.test.{ testM, _ }
import zio.duration._
import zio.test.TestAspect.{ nonFlaky, sequential, timeout }
import zio.random.Random

object ZMXServerSpec extends DefaultRunnableSpec {

  private def zmxConfig = ZMXConfig.empty
  private val zmxClient = new ZMXClient(zmxConfig)

  sealed abstract class CliCmd(val in: Chunk[String], val assertion: Assertion[String])
  case object Test extends CliCmd(Chunk("test"), equalTo("+This is a TEST\r\n"))
  case object Unknown extends CliCmd(Chunk("unknown"), equalTo("-UNKNOWN COMMAND: `unknown`!\r\n"))
  case object Dump extends CliCmd(Chunk("dump"), equalTo("*0\r\n"))
  case object Metrics extends CliCmd(Chunk("metrics"),
    containsString("capacity:") && containsString("concurrency:"))

  private val genCliCmd: Gen[Random with Sized, CliCmd] = Gen.oneOf(
    Gen.const[CliCmd](Test),
    Gen.const[CliCmd](Unknown),
    Gen.const[CliCmd](Dump),
    Gen.const[CliCmd](Metrics)
  )


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
      } @@ timeout(5.seconds),
      testM("Is properly answer hundred of commands in parallel 5 threads") {
        server.use(_ => ZIO.mergeAllParN(5) {
          Stream.continually(()).take(100).map(_ =>
            for {
              cmd <- genCliCmd.runHead.get
              out <- zmxClient.sendCommand(cmd.in)
            } yield assert(out)(cmd.assertion))
        }(assertCompletes)(_ && _))
      } @@ timeout(20.seconds)
    ) @@ sequential @@ nonFlaky

  val server             = ZMXServer.make(ZMXConfig("localhost", 1111, debug = true))
  val openAndCloseServer = server.use(_ => ZIO.unit)
}
