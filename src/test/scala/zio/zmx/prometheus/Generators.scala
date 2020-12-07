package zio.zmx.prometheus

import zio.test._

trait Generators {
  val genPosDouble           = Gen.double(0.0, Double.MaxValue)
  val genNegDouble           = Gen.double(Double.MinValue, 0.0)
  def genSomeDoubles(n: Int) = Gen.chunkOfBounded(1, n)(genPosDouble)
}
