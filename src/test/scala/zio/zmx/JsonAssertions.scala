package zio.zmx

import zio.json.ast._
import zio.test.Assertion
import zio.test.Assertion._
import zio.test.TestArrow
import zio.test.internal.SmartAssertions
import zio.zmx.newrelic.NewRelicEncoder

object JsonAssertions {

  val isJsonObj =
    SmartAssertions.as[Json, Json.Obj].withCode("isJson.Obj") >>> TestArrow.fromFunction(
      _.fields,
    )

  val hasAttributes =
    isJsonObj >>> TestArrow.fromFunction(_.find(_._1 == "attributes").map(_._2)) >>> SmartAssertions.isSome.withCode(
      "hasAttributes",
    )

  def hasFieldWithValue(fieldName: String, fieldValue: Json) =
    hasField(equalTo(fieldName -> fieldValue))

  def hasField(assertion: Assertion[(String, Json)]): Assertion[Json] =
    Assertion[Json] {
      isJsonObj >>> exists(assertion).arrow.withCode("hasField")
    }

  object NewRelicAssertions {

    def hasCommonFields(name: String, metricType: String, timestamp: Long) =
      hasFieldWithValue("name", Json.Str(name)) && hasFieldWithValue(
        "type",
        Json.Str(metricType),
      ) && hasFieldWithValue(
        "timestamp",
        Json.Num(timestamp),
      )

    def hasAttribute(name: String, value: Json): Assertion[Json] =
      Assertion[Json] {
        val assertion = hasFieldWithValue(name, value)
        hasAttributes >>> assertion.arrow

      }

    def hasCounter(
      name: String,
      value: Long,
      frequencyName: String,
      timestamp: Long,
      intervalInMillis: Long,
    ): Assertion[Iterable[Json]] = {

      val assertion = hasFieldWithValue("name", Json.Str(name)) &&
        hasFieldWithValue(
          "value",
          Json.Num(value.toDouble),
        ) &&
        hasCommonFields(name, "count", timestamp) &&
        hasFieldWithValue("interval.ms", Json.Num(intervalInMillis)) &&
        hasAttribute(
          NewRelicEncoder.frequencyTagName,
          Json.Str(frequencyName),
        ) &&
        hasAttribute(
          "zmx.type",
          Json.Str("Frequency"),
        )

      exists(assertion)
    }
  }

}
