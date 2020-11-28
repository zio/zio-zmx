package zio.zmx.prometheus

import zio.duration._

import zio.test._
import zio.test.TestAspect._

object QuantileSpec extends DefaultRunnableSpec with Generators {

  override def spec = suite("A Quantile should")(
  ) @@ timed @@ timeoutWarning(60.seconds) @@ parallel

}
