package zio.zmx.diagnostics.parser

import zio.test.Assertion._
import zio.test._

object RespSpec extends DefaultRunnableSpec {
  override def spec =
    suite("RespSpec")(RespTestCases.scenarios.map {

      case RespTestCases.Success(description, input, expectedResult, expectedReserialization) =>
        testM(s"Resp implementation parses and serializes **$description** correctly") {
          for {
            parsingResult      <- Resp.parse(input)
            serializationResult = expectedResult.serialize
          } yield assert(parsingResult)(equalTo(expectedResult)) &&
            assert(serializationResult)(equalTo(expectedReserialization))
        }

      case RespTestCases.Failure(description, input, expectedParsingError) =>
        testM(s"Resp implementation fails on **$description** correctly") {
          for {
            parsingError <- Resp.parse(input).flip
          } yield assert(parsingError)(equalTo(expectedParsingError))
        }

    }: _*)
}
