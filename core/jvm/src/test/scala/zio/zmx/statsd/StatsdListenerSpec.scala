package zio.zmx.statsd

import zio._
import zio.test._
import zio.test.TestAspect._

object StatsdListenerSpec extends ZIOSpecDefault {

  override def spec = suite("The StatsdListener should")(
    sendCounter,
  ) @@ timed @@ timeoutWarning(60.seconds)

  private val sendCounter = test("send counter updates") {
    assertTrue(false)
  }
}
