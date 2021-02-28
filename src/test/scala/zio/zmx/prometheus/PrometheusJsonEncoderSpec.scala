package zio.zmx.prometheus

import zio.Chunk
import zio.test.Assertion._
import zio.test._
import zio.zmx.Generators
import zio.zmx.prometheus.PrometheusJsonEncoder._
import zio.json._

object PrometheusJsonEncoderSpec extends DefaultRunnableSpec with Generators {

  val labels = Chunk("k1" -> "v1", "k2" -> "v2")

  override def spec = suite("PrometheusJsonEncoder")(
    test("encode Counter")(
      assert(PMetric.incCounter(PMetric.counter("myName", "Some Counter Help", labels)).toJsonPretty
      )(
        equalTo(
          """{
            |  "name" : "myName",
            |  "help" : "Some Counter Help",
            |  "labels" : [["k1", "v1"], ["k2", "v2"]],
            |  "details" : {
            |    "CounterImpl" : {
            |      "count" : 1.0
            |    }
            |  }
            |}""".stripMargin)
      )
    ),
    test("encode Gauge")(
      assert(PMetric
        .incGauge(
          PMetric.gauge("myGauge", "Some Gauge Help", labels), 100
        ).toJsonPretty
      )(
        equalTo(
          """{
            |  "name" : "myGauge",
            |  "help" : "Some Gauge Help",
            |  "labels" : [["k1", "v1"], ["k2", "v2"]],
            |  "details" : {
            |    "GaugeImpl" : {
            |      "value" : 100.0
            |    }
            |  }
            |}""".stripMargin)
      )
    ),
    test("encode Histogram")(
      assert(PMetric.histogram("myHistogram", "Some Histogram Help", labels, PMetric.Buckets.Linear(0, 10, 10)).toJsonPretty
      )(
        equalTo(
          """{
            |  "name" : "myHistogram",
            |  "help" : "Some Histogram Help",
            |  "labels" : [["k1", "v1"], ["k2", "v2"]],
            |  "details" : {
            |    "HistogramImpl" : {
            |      "buckets" : [[0.0, 0.0], [10.0, 0.0], [20.0, 0.0], [30.0, 0.0], [40.0, 0.0], [50.0, 0.0], [60.0, 0.0], [70.0, 0.0], [80.0, 0.0], [90.0, 0.0], [1.7976931348623157E308, 0.0]],
            |      "count" : 0.0,
            |      "sum" : 0.0
            |    }
            |  }
            |}""".stripMargin)
      )
    ),
    test("encode Summary")(
      assert(   PMetric
        .summary("mySummary", "Some  Help", labels)(
          Quantile(0.2, 0.03).get,
          Quantile(0.5, 0.03).get,
          Quantile(0.9, 0.03).get
        ).toJsonPretty
      )(
        equalTo(
          """{
            |  "name" : "mySummary",
            |  "help" : "Some  Help",
            |  "labels" : [["k1", "v1"], ["k2", "v2"]],
            |  "details" : {
            |    "SummaryImpl" : {
            |      "samples" : {
            |        "maxAge" : "PT1H",
            |        "maxSize" : 1024,
            |        "samples" : []
            |      },
            |      "quantiles" : [{
            |        "phi" : 0.2,
            |        "error" : 0.03
            |      }, {
            |        "phi" : 0.5,
            |        "error" : 0.03
            |      }, {
            |        "phi" : 0.9,
            |        "error" : 0.03
            |      }],
            |      "count" : 0.0,
            |      "sum" : 0.0
            |    }
            |  }
            |}""".stripMargin)
      )
    )
  )

}
