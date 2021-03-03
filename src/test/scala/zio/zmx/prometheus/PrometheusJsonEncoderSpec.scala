package zio.zmx.prometheus

import zio.Chunk
import zio.test.Assertion._
import zio.test.{ assert, _ }
import zio.zmx.Generators
import zio.json._
import zio.zmx.prometheus.PMetric.Details

import java.time.{ LocalDateTime, ZoneOffset }

/**
 * This is little complicated spec.
 * We use custom json encoder because we don't want too many dependencies
 * Custom encoder produces compact json, we compare the compact json with derive zio-json version
 * and then we use zio-json to pretty print the json and compare it with the string in the code.
 * This way I can we can be sure we didn't miss any field.
 * And with pretty printed json we can also look at results if there is not any obvious error
 */
object PrometheusJsonEncoderSpec extends DefaultRunnableSpec with Generators {

  implicit val quantileEncoder: JsonEncoder[Quantile] =
    DeriveJsonEncoder.gen[Quantile]

  implicit val timeSeriesEncoder: JsonEncoder[TimeSeries] =
    DeriveJsonEncoder.gen[TimeSeries]

  implicit val detailsEncoder: JsonEncoder[Details] =
    DeriveJsonEncoder.gen[Details]

  implicit val pmetricEncoder: JsonEncoder[PMetric] =
    DeriveJsonEncoder.gen[PMetric]

  val labels = Chunk("k1" -> "v1", "k2" -> "v2")

  def encode(o: Option[PMetric]) =
    o.fold("null")(PrometheusJsonEncoder.encodePMetric)

  def instant(year: Int, month: Int, dayOfMonth: Int) =
    LocalDateTime.of(year, month, dayOfMonth, 0, 0, 0).atOffset(ZoneOffset.UTC).toInstant

  override def spec = suite("PrometheusJsonEncoder")(
    test("encode Counter") {
      val m = PMetric.incCounter(PMetric.counter("myName", "Some Counter Help", labels))
      assert(encode(m))(equalTo(m.toJson)) &&
      assert(m.toJsonPretty)(
        equalTo("""{
                  |  "name" : "myName",
                  |  "help" : "Some Counter Help",
                  |  "labels" : [["k1", "v1"], ["k2", "v2"]],
                  |  "details" : {
                  |    "Counter" : {
                  |      "count" : 1.0
                  |    }
                  |  }
                  |}""".stripMargin)
      )
    },
    test("encode Gauge") {
      val m = PMetric.incGauge(PMetric.gauge("myGauge", "Some Gauge Help", labels), 100)
      assert(encode(m))(equalTo(m.toJson)) &&
      assert(m.toJsonPretty)(
        equalTo("""{
                  |  "name" : "myGauge",
                  |  "help" : "Some Gauge Help",
                  |  "labels" : [["k1", "v1"], ["k2", "v2"]],
                  |  "details" : {
                  |    "Gauge" : {
                  |      "value" : 100.0
                  |    }
                  |  }
                  |}""".stripMargin)
      )
    },
    test("encode Histogram") {
      val m = PMetric.histogram("myHistogram", "Some Histogram Help", labels, PMetric.Buckets.Linear(0, 10, 10))
      assert(encode(m))(equalTo(m.toJson)) &&
      assert(m.toJsonPretty)(
        equalTo(
          """{
            |  "name" : "myHistogram",
            |  "help" : "Some Histogram Help",
            |  "labels" : [["k1", "v1"], ["k2", "v2"]],
            |  "details" : {
            |    "Histogram" : {
            |      "buckets" : [[0.0, 0.0], [10.0, 0.0], [20.0, 0.0], [30.0, 0.0], [40.0, 0.0], [50.0, 0.0], [60.0, 0.0], [70.0, 0.0], [80.0, 0.0], [90.0, 0.0], [1.7976931348623157E308, 0.0]],
            |      "count" : 0.0,
            |      "sum" : 0.0
            |    }
            |  }
            |}""".stripMargin
        )
      )
    },
    test("encode Summary") {
      val m = PMetric
        .summary("mySummary", "Some  Help", labels, samples = Chunk((123, instant(2021, 3, 2))))(
          Quantile(0.2, 0.03).get,
          Quantile(0.5, 0.03).get,
          Quantile(0.9, 0.03).get
        )
      assert(encode(m))(equalTo(m.toJson)) &&
      assert(m.toJsonPretty)(
        equalTo("""{
                  |  "name" : "mySummary",
                  |  "help" : "Some  Help",
                  |  "labels" : [["k1", "v1"], ["k2", "v2"]],
                  |  "details" : {
                  |    "Summary" : {
                  |      "samples" : {
                  |        "maxAge" : "PT1H",
                  |        "maxSize" : 1024,
                  |        "samples" : [[123.0, "2021-03-02T00:00:00Z"]]
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
    }
  )

}
