package zio.zmx.diagnostics.parser

import java.nio.charset.StandardCharsets.UTF_8

import zio.Chunk
import zio.zmx.diagnostics.parser.Resp._

object RespTestCases {

  /** Helper for creating `Chunk[Byte]` from `String` */
  private def resp(value: String): Chunk[Byte] =
    Chunk.fromArray(value.getBytes(UTF_8))

  /** Models for scenarios */
  sealed trait TestCase

  final case class Success(
    description: String,
    input: Chunk[Byte],
    expectedResult: RespValue,
    expectedReserialization: Chunk[Byte]
  ) extends TestCase

  object Success {
    def apply(description: String, input: Chunk[Byte], expectedResult: RespValue): Success =
      Success(description, input, expectedResult, expectedReserialization = input)
  }

  final case class Failure(
    description: String,
    input: Chunk[Byte],
    expectedParsingError: ParsingError
  ) extends TestCase

  /** Scenarios */
  final val scenarios = List[TestCase](
    /** General broken inputs */
    Failure(
      "empty input",
      resp(""),
      UnexpectedEndOfData
    ),
    Failure(
      "unknown header type",
      resp("?"),
      UnknownHeader('?')
    ),
    Failure(
      "cut input",
      resp("+"),
      UnexpectedEndOfData
    ),
    /** RESP Simple Strings */
    Success(
      "basic Simple String",
      resp("+OK\r\n"),
      SimpleString("OK")
    ),
    Success(
      "empty Simple String",
      resp("+\r\n"),
      SimpleString("")
    ),
    Failure(
      "unterminated Simple String",
      resp("+Test"),
      UnexpectedEndOfData
    ),
    Failure(
      "broken (missing LF) Simple String",
      resp("+Test\r"),
      UnexpectedEndOfData
    ),
    Failure(
      "broken (missing CR) Simple String",
      resp("+Test\n"),
      UnexpectedEndOfData
    ),
    Failure(
      "broken (LF CR instead of CR LF) Simple String",
      resp("+Test\n\r"),
      UnexpectedEndOfData
    ),
    Failure(
      "excessive data after Simple String",
      resp("+Test\r\nABC"),
      ExcessiveData(Chunk(65, 66, 67))
    ),
    Success(
      "non-ASCII Simple String",
      resp("+Żółć!\r\n"),
      SimpleString("Żółć!")
    ),
    /** RESP Errors */
    Success(
      "basic Error",
      resp("-Error message\r\n"),
      Error("Error message")
    ),
    Success(
      "empty Error",
      resp("-\r\n"),
      Error("")
    ),
    Failure(
      "unterminated Error",
      resp("-Test"),
      UnexpectedEndOfData
    ),
    Failure(
      "broken (missing LF) Error",
      resp("-Test\r"),
      UnexpectedEndOfData
    ),
    Failure(
      "broken (missing CR) Error",
      resp("-Test\n"),
      UnexpectedEndOfData
    ),
    Failure(
      "broken (LF CR instead of CR LF) Error",
      resp("-Test\n\r"),
      UnexpectedEndOfData
    ),
    Success(
      "non-ASCII Error",
      resp("-Żółć!\r\n"),
      Error("Żółć!")
    ),
    /** RESP Integers */
    Success(
      "zero as Integer",
      resp(":0\r\n"),
      Integer(0)
    ),
    Success(
      "negative zero as Integer",
      resp(":-0\r\n"),
      Integer(0),
      resp(":0\r\n")
    ),
    Success(
      "positive number as Integer",
      resp(":1000\r\n"),
      Integer(1000)
    ),
    Success(
      "negative number as Integer",
      resp(":-1337\r\n"),
      Integer(-1337)
    ),
    Success(
      "big positive number as Integer",
      resp(s":${Long.MaxValue}\r\n"),
      Integer(Long.MaxValue)
    ),
    Success(
      "big negative number as Integer",
      resp(s":${Long.MinValue}\r\n"),
      Integer(Long.MinValue)
    ),
    Success(
      "zero-padded number with plus sign as Integer",
      resp(":+00017\r\n"),
      Integer(17),
      resp(":17\r\n")
    ),
    Failure(
      "non-decimal as Integer",
      resp(":0xFF\r\n"),
      InvalidInteger("0xFF")
    ),
    Failure(
      "non-number non-ASCII text as Integer",
      resp(":Żółć\r\n"),
      InvalidInteger("Żółć")
    ),
    Failure(
      "unterminated Integer",
      resp(":0"),
      UnexpectedEndOfData
    ),
    /** RESP Bulk Strings */
    Success(
      "basic Bulk String",
      resp("$6\r\nfoobar\r\n"),
      BulkString("foobar")
    ),
    Success(
      "empty Bulk String",
      resp("$0\r\n\r\n"),
      BulkString("")
    ),
    Success(
      "Null Bulk String",
      resp("$-1\r\n"),
      NoValue
    ),
    Success(
      "Bulk String with new lines",
      resp("$31\r\nalpha beta\rgamma\ndelta\r\nepsilon\r\n"),
      BulkString("alpha beta\rgamma\ndelta\r\nepsilon")
    ),
    Success(
      "non-ASCII Bulk String",
      /* Encoded with UTF-8 this Bulk String has length of 15 bytes.
       *
       * {{{
       *   >>> x = "Żółć it is!".encode('utf-8')
       *   >>> x
       *   b'\xc5\xbb\xc3\xb3\xc5\x82\xc4\x87 it is!'
       *   >>> len(x)
       *   15
       * }}}
       */
      resp("$15\r\nŻółć it is!\r\n"),
      BulkString("Żółć it is!")
    ),
    Failure(
      "negative length Bulk String",
      resp("$-3\r\nfoo\r\n"),
      InvalidSize(-3)
    ),
    Failure(
      "too small length Bulk String",
      resp("$2\r\nfoo\r\n"),
      MalformedData
    ),
    Failure(
      "too big length Bulk String",
      resp("$9\r\nfoo\r\n"),
      UnexpectedEndOfData
    ),
    Failure(
      "cut size Bulk String",
      resp("$9"),
      UnexpectedEndOfData
    ),
    Failure(
      "cut data Bulk String",
      resp("$3\r\nfoo"),
      UnexpectedEndOfData
    ),
    Failure(
      "non-number length Bulk String",
      resp("$bar\r\nfoo\r\n"),
      InvalidInteger("bar")
    ),
    Failure(
      "out of range length Bulk String",
      resp("$9999999999999999999999999\r\nfoo\r\n"),
      InvalidInteger("9999999999999999999999999")
    ),
    /** RESP Arrays */
    Success(
      "empty Array",
      resp("*0\r\n"),
      Array(Chunk.empty)
    ),
    Success(
      "Array with two Bulk Strings",
      resp("*2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n"),
      Array(Chunk(BulkString("foo"), BulkString("bar")))
    ),
    Success(
      "Array with three Integers",
      resp("*3\r\n:1\r\n:2\r\n:3\r\n"),
      Array(Integer(1), Integer(2), Integer(3))
    ),
    Success(
      "Array with mixed types",
      resp("*5\r\n:1\r\n:2\r\n:3\r\n:4\r\n$6\r\nfoobar\r\n"),
      Array(Integer(1), Integer(2), Integer(3), Integer(4), BulkString("foobar"))
    ),
    Success(
      "Null Array",
      resp("*-1\r\n"),
      NoValue,
      resp("$-1\r\n") // `NoValue` is serialized as Null Bulk String by default
    ),
    Success(
      "Array with nested Array",
      resp("*2\r\n*3\r\n:1\r\n:2\r\n:3\r\n*2\r\n+Foo\r\n-Bar\r\n"),
      Array(Array(Integer(1), Integer(2), Integer(3)), Array(SimpleString("Foo"), Error("Bar")))
    ),
    Success(
      "Array with Null values",
      resp("*3\r\n$3\r\nfoo\r\n$-1\r\n$3\r\nbar\r\n"),
      Array(BulkString("foo"), NoValue, BulkString("bar"))
    ),
    Success(
      "Array with example Redis command",
      resp("*2\r\n$4\r\nLLEN\r\n$6\r\nmylist\r\n"),
      Array(BulkString("LLEN"), BulkString("mylist"))
    ),
    Failure(
      "malformed Bulk String in Array",
      resp("*1\r\n$3\r\ndump\r\n"),
      MalformedData
    ),
    Failure(
      "negative Array size",
      resp("*-5\r\n..."),
      InvalidSize(-5)
    ),
    Failure(
      "too big Array size",
      resp("*5\r\n"),
      UnexpectedEndOfData
    ),
    Failure(
      "too small Array size",
      resp("*2\r\n:1\r\n:2\r\n:3\r\n"),
      ExcessiveData(Chunk(58, 51, 13, 10))
    ),
    Failure(
      "cut data in Array",
      resp("*3\r\n:1\r\n:2\r\n:3\r"),
      UnexpectedEndOfData
    ),
    Failure(
      "cut Array size",
      resp("*5"),
      UnexpectedEndOfData
    ),
    Failure(
      "non-number Array size",
      resp("*foo\r\n:1\r\n:2\r\n:3\r\n"),
      InvalidInteger("foo")
    ),
    Failure(
      "out of range Array size",
      resp("*9999999999999999999999999\r\n:1\r\n:2\r\n:3\r\n"),
      InvalidInteger("9999999999999999999999999")
    ),
    Failure(
      "broken item in Array",
      resp("*3\r\n:1\r\n:broken\r\n:3\r\n"),
      InvalidInteger("broken")
    )
  )

}
