package zio

import zio.test._
import zio.test.environment._
import zio.zmx._

object ZMXSupervisorSpec extends DefaultRunnableSpec {

  val t = ZMXSupervisor.value.map(_.dumpAll)

  //TODO WIP in https://github.com/zio/zio-zmx/issues/112
  def spec: ZSpec[
    Annotations with Live with Sized with TestClock with TestConfig with TestConsole with TestRandom with TestSystem with ZEnv,
    Any
  ] =
    suite("ZMX Supervisor Spec")(
      suite("dump all")(
        testM("no dumps") {
            assertM(ZMXSupervisor.value.flatMap(_.dumpAll.runCollect))(Assertion.isEmpty)
        }
      )
    )
}
