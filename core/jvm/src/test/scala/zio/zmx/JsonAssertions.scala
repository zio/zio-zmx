package zio.zmx

import zio.json.ast._
import zio.test.Assertion
import zio.test.Assertion._
import zio.test.Assertion.Render._
import zio.zmx.newrelic.NewRelicEncoder

object JsonAssertions {

  def hasFieldWithValue(fieldName: String, fieldValue: Json) =
    hasField(equalTo(fieldName -> fieldValue))

  def hasField(assertion: Assertion[(String, Json)]): Assertion[Json] =
    Assertion.assertionRec("hasField")(param(assertion))(exists(assertion)) {
      case Json.Obj(fields) => Some(fields)
      case _                => None
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

    def hasAttribute(name: String, value: Json): Assertion[Json] = {
      val assertion = hasFieldWithValue(name, value)
      Assertion.assertionRec("hasAttribute")(param(assertion))(assertion) { json =>
        json match {
          case Json.Obj(fields) => fields.find(_._1 == "attributes").map(_._2)
          case _                => None
        }
      }
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
          "count",
          Json.Num(value.toDouble),
        ) &&
        hasCommonFields(name, "counter", timestamp) &&
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
