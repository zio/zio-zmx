package zio.zmx.prometheus

import zio.Chunk
import zio.duration._

import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

object QuantileSpec extends DefaultRunnableSpec with Generators with WithDoubleOrdering {

  // Just generate a chunk of doubles as a sample
  private val genSamples = genSomeDoubles(100)

  // generate some arbitrary Quantiles
  private val genQuantile = Gen.chunkOfBounded(1, 10)(Gen.double(0.1, 0.9)).map { cd =>
    cd.distinct.map(d => Quantile(d, 0.01).get)
  }

  // A generator to generate a sample and some Quantiles
  private val genFull = genSamples.crossWith(genQuantile) { case (a, b) => (a, b) }

  override def spec = suite("A Quantile should")(
    noQuantiles,
    fullQuantile,
    someQuantiles
  ) @@ timed @@ timeoutWarning(60.seconds) @@ parallel

  private val noQuantiles = testM("handle empty quentile list")(checkM(genSamples) { samples =>
    for {
      qs <- zio.ZIO.succeed(Quantile.calculateQuantiles(samples, Chunk.empty))
    } yield assert(qs)(isEmpty)
  })

  private val fullQuantile = testM("Calculate the 1.0 quantile correctly")(checkM(genSamples) { samples =>
    for {
      qs <- zio.ZIO.succeed(Quantile.calculateQuantiles(samples, Chunk(Quantile(1.0, 0).get)))
    } yield assert(qs.length)(equalTo(1)) &&
      assert(qs.head._2)(equalTo(Some(samples.max(dblOrdering))))
  })

  private def verifyQuantile(samples: Chunk[Double], q: Quantile, v: Option[Double]): Boolean =
    if (samples.isEmpty) {
      v.isEmpty
    } else {

      val desired  = samples.length * q.phi
      val maxCount = desired + desired * q.error / 2

      val result = v match {
        case None    =>
          val sh = samples.min(dblOrdering)
          val mc = samples.count(_ == sh)
          mc > maxCount
        case Some(v) =>
          // Just verify the percentage
          val lc = samples.count(_ <= v)
          lc <= maxCount
      }

      if (!result) {
        println(s"$v ## $desired - $maxCount - $q - $samples ===> $result")
      }

      result
    }

  private val someQuantiles = testM("handle arbitrary Quantiles")(checkM(genFull) { case (s, q) =>
    for {
      qs    <- zio.ZIO.succeed(Quantile.calculateQuantiles(s, q))
      verify = qs.map(rq => verifyQuantile(s, rq._1, rq._2))
    } yield assert(verify.forall(x => x))(isTrue)
  })

}
