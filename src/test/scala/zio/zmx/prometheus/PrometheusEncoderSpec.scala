package zio.zmx.prometheus

import zio.test._

object PrometheusEncoderSpec extends DefaultRunnableSpec {

  /**
   * 1. Define Counter, Gauge, Histogram, and Summary
   * 2. Define expected outcomes, respectively
   * 3. Use TestClock for timestamps?
   * 4. Run & Assert
   */

  //val testData = ???

  def spec = suite("PrometheusEncoderSpec")(
    suite("Counter suite")(
      /**
       * create counter
       * update counter (create new counter)
       * encode -> String -> Assert string == testData
       */

      test("Counter should encode") {

        val expectedOutput: String = """# TYPE myCounter counter
                                       |myCounter {a="b"} 0.0 """.stripMargin
        val baseCounter            = Metric.counter("myCounter", Map("a" -> "b"))
        val encoded                = PrometheusEncoder.encode(List(baseCounter), None)
        assert(encoded)(Assertion.equalTo(expectedOutput))
      },
      test("Counter should inc by one") {
        val expectedOutput: String = """# TYPE myCounter counter
                                       |myCounter {a="b"} 1.0 """.stripMargin
        val baseCounter            = Metric.counter("myCounter", Map("a" -> "b"))
        val incCounter             = baseCounter.inc
        val encoded                = PrometheusEncoder.encode(List(incCounter), None)
        assert(encoded)(Assertion.equalTo(expectedOutput))
      } /*,
      test("Counter should Inc by n") {
        for {
          c <- Task(Counter("myCounter", Some("You know I need someone!"), Map("a" -> "b"), 0).inc(41))
          r <- c.get
        } yield PrometheusEncoder.encode(List(r), None)
        val encoded = PrometheusEncoder.encode(List(incNCounter), None)
        assert(encoded)(Assertion.equalTo(""))
      }*/
    )
  )

}
