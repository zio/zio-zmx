package zio.zmx.prometheus

import zio.test._
import Metric.{ BucketType, Counter, Gauge }

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

      test("Counter should encode (w/ help)") {

        val expectedOutput: String = """# TYPE baseCounter counter
                                       |# HELP baseCounter I need somebody!
                                       |baseCounter {a="b"} 0.0 """.stripMargin

        val baseCounter = Counter("baseCounter", Some("I need somebody!"), Map("a" -> "b"), 0)
        val baseEncoded = PrometheusEncoder.encode(List(baseCounter), None)
        assert(baseEncoded)(Assertion.equalTo(expectedOutput))
      },
      test("Counter should encode (w/o help)") {

        val expectedOutput: String = """# TYPE baseCounter counter
                                       |baseCounter {a="b"} 0.0 """.stripMargin

        val baseCounter = Counter("baseCounter", None, Map("a" -> "b"), 0)
        val baseEncoded = PrometheusEncoder.encode(List(baseCounter), None)
        assert(baseEncoded)(Assertion.equalTo(expectedOutput))
      },
      test("Counter should encode (from smart constructor)") {

        val expectedOutput: String = """# TYPE baseCounter counter
                                       |# HELP baseCounter not just anybody!
                                       |baseCounter {a="b"} 0.0 """.stripMargin

        val smartCounter = Metric.counter(
          "baseCounter",
          Some("not just anybody!"),
          Map(
            "a" ->
              "b"
          )
        )
        val smartEncoded = PrometheusEncoder.encode(List(smartCounter), None)
        assert(smartEncoded)(Assertion.equalTo(expectedOutput))
      },
      test("Counter should inc by one") {
        val expectedOutput: String = """# TYPE incCounter counter
                                       |incCounter {a="b"} 1.0 """.stripMargin
        val incCounter             = Metric.counter("incCounter", None, Map("a" -> "b")).inc
        val encoded                = PrometheusEncoder.encode(List(incCounter), None)
        assert(encoded)(Assertion.equalTo(expectedOutput))
      },
      test("Counter should inc by n") {
        val expectedOutput: String = """# TYPE incNCounter counter
                                       |incNCounter {a="b"} 42.0 """.stripMargin

        val incNCounter = Counter("incNCounter", None, Map("a" -> "b"), 0).inc(42)
        val encoded     = PrometheusEncoder.encode(List(incNCounter.get), None)
        assert(encoded)(Assertion.equalTo(expectedOutput))
      }
    ),
    suite("Gauge suite")(
      test("Gauge should encode (w/ help)") {
        val expectedOutput: String = """# TYPE myGauge gauge
                                       |# HELP myGauge is underway
                                       |myGauge {badum="tss"} 0.0 """.stripMargin

        val myGauge = Gauge("myGauge", Some("is underway"), Map("badum" -> "tss"), 0)
        val encoded = PrometheusEncoder.encode(List(myGauge), None)
        assert(encoded)(Assertion.equalTo(expectedOutput))
      },
      test("Gauge should encode (w/o help)") {
        val expectedOutput: String = """# TYPE myGauge gauge
                                       |myGauge {badum="tss"} 0.0 """.stripMargin

        val myGauge = Gauge("myGauge", None, Map("badum" -> "tss"), 0)
        val encoded = PrometheusEncoder.encode(List(myGauge), None)
        assert(encoded)(Assertion.equalTo(expectedOutput))
      },
      test("Gauge should encode (from smart constructor)") {
        val expectedOutput: String = """# TYPE smartGauge gauge
                                       |# HELP smartGauge is underway
                                       |smartGauge {badum="tss"} 0.0 """.stripMargin

        val smartGauge = Metric.gauge("smartGauge", Some("is underway"), Map("badum" -> "tss"))
        val encoded    = PrometheusEncoder.encode(List(smartGauge), None)
        assert(encoded)(Assertion.equalTo(expectedOutput))
      },
      test("Gauge should inc") {
        val expectedOutput: String = """# TYPE myGauge gauge
                                       |# HELP myGauge is underway
                                       |myGauge {badum="tss"} 1.0 """.stripMargin

        val myGauge = Gauge("myGauge", Some("is underway"), Map("badum" -> "tss"), 0).inc
        val encoded = PrometheusEncoder.encode(List(myGauge), None)
        assert(encoded)(Assertion.equalTo(expectedOutput))
      },
      test("Gauge should inc by n") {
        val expectedOutput: String = """# TYPE myGauge gauge
                                       |# HELP myGauge is underway
                                       |myGauge {badum="tss"} 1337.0 """.stripMargin

        val myGauge = Gauge("myGauge", Some("is underway"), Map("badum" -> "tss"), 0).inc(1337)
        val encoded = PrometheusEncoder.encode(List(myGauge), None)
        assert(encoded)(Assertion.equalTo(expectedOutput))
      },
      test("Gauge should dec") {
        val expectedOutput: String = """# TYPE myGauge gauge
                                       |# HELP myGauge is underway
                                       |myGauge {badum="tss"} -1.0 """.stripMargin

        val myGauge = Gauge("myGauge", Some("is underway"), Map("badum" -> "tss"), 0).dec
        val encoded = PrometheusEncoder.encode(List(myGauge), None)
        assert(encoded)(Assertion.equalTo(expectedOutput))
      },
      test("Gauge should dec by n") {
        val expectedOutput: String = """# TYPE myGauge gauge
                                       |# HELP myGauge is underway
                                       |myGauge {badum="tss"} -123.0 """.stripMargin

        val myGauge = Gauge("myGauge", Some("is underway"), Map("badum" -> "tss"), 0).dec(123)
        val encoded = PrometheusEncoder.encode(List(myGauge), None)
        assert(encoded)(Assertion.equalTo(expectedOutput))
      },
      test("Gauge should set to n") {
        val myGauge                =
          Gauge("myGauge", Some("one hundred billion dollars!"), Map("badum" -> "tss"), 0).set(100000000000d)

        val expectedOutput: String = s"""# TYPE myGauge gauge
                                        |# HELP myGauge one hundred billion dollars!
                                        |myGauge {badum="tss"} ${myGauge.value.toString} """.stripMargin

        val encoded = PrometheusEncoder.encode(List(myGauge), None)
        assert(encoded)(Assertion.equalTo(expectedOutput))
      }
    ),
    suite("Histogram suite")(test("Histogram should encode") {

      val myHistogram            =
        Metric.histogram("myHistogram", Some("help text"), Map("k1" -> "v1", "k2" -> "v2"), BucketType.Linear(0, 10, 1))

      val expectedOutput: String = s"""# TYPE myHistogram histogram
                                      |# HELP myHistogram help text
                                      |myHistogram_bucket {k1="v1",k2="v2",le="0.0"} 0.0 
                                      |myHistogram_bucket {k1="v1",k2="v2",le="1.7976931348623157E308"} 0.0 
                                      |myHistogram_sum {k1="v1",k2="v2"} 0.0 
                                      |myHistogram_count {k1="v1",k2="v2"} 0.0 """.stripMargin

      val encoded = PrometheusEncoder.encode(List(myHistogram), None)

      assert(encoded.strip)(Assertion.equalTo(expectedOutput.strip))
    })
  )

}
