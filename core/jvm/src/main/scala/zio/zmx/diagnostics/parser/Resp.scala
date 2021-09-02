package zio.zmx.diagnostics.parser

import java.nio.charset.StandardCharsets.UTF_8

import zio.{ Chunk, IO }

import scala.annotation.tailrec

/**
 * Implementation of RESP (REdis Serialization Protocol)
 *
 * Protocol specification: https://redis.io/topics/protocol
 *
 * Following simplifications were made in this implementation:
 *
 *  - Strings in __Simple Strings__ and __Errors__ have `\r` and `\n` removed automatically
 *      upon creation, see: [[Resp.normalized]] method for details,
 *
 *  - When serializing strings, they are encoded using UTF-8 in [[Resp.encode]] method,
 *
 *  - While the __Bulk Strings__ could be used to store and transfer arbitrary binary data
 *      for simplification of the API we limit them to just correct UTF-8 bytes
 *      that are decoded to [[String]] type.
 */
private[zmx] object Resp {

  /**
   * RESP protocol internals
   */

  /** Headers of RESP data types */
  final private val SimpleStringHeader: Byte = '+'
  final private val ErrorHeader: Byte        = '-'
  final private val IntegerHeader: Byte      = ':'
  final private val BulkStringHeader: Byte   = '$'
  final private val ArrayHeader: Byte        = '*'

  /** Different parts of the protocol are always terminated with `CRLF` */
  final private val CarriageReturn: Byte    = '\r'
  final private val LineFeed: Byte          = '\n'
  final private val Terminator: Chunk[Byte] = Chunk(CarriageReturn, LineFeed)

  /**
   * In accordance with the RESP specification, __Simple Strings__ and __Errors__ both
   * represent strings that cannot contain a CR or LF characters (no newlines are allowed).
   * To simplify the API and avoid need for refined types and refining code we just
   * remove all forbidden characters from input upon creation.
   */
  private def normalized(value: String): String =
    value.filterNot(Terminator.contains(_))

  /** Strings are encoded using UTF-8 */
  private def encode(value: String): Chunk[Byte] =
    Chunk.fromArray(value.getBytes(UTF_8))

  /** Integers are represented as decimals */
  private def encode(value: Long): Chunk[Byte] =
    encode(value.toString)

  /**
   * Bytes are expected to be UTF-8 encoded
   *
   * Default JVM UTF-8 decoder cannot fail because it replaces malformed-input and unmappable-character
   * sequences automatically with the Unicode replacement character.
   *
   * For detection of malformed UTF-8 bytes sequences custom `CharsetDecoder` with `CodingErrorAction.REPORT`
   * action for malformed-input and unmappable-character sequences could be used, then wrapped with `Try`/`IO`
   * to handle that properly. For simplification we assume only correct strings should be sent.
   */
  private def decode(bytes: Chunk[Byte]): String =
    new String(bytes.toArray, UTF_8)

  /**
   * RESP data types and serialization
   */

  /** General type that all RESP data types extend */
  sealed trait RespValue {
    def serialize: Chunk[Byte]
  }

  /** RESP Simple Strings */
  sealed abstract case class SimpleString private (value: String) extends RespValue {
    override def serialize: Chunk[Byte] =
      SimpleStringHeader +: encode(value) ++: Terminator
  }
  object SimpleString {
    def apply(value: String): SimpleString =
      new SimpleString(normalized(value)) {}
  }

  /** RESP Errors */
  sealed abstract case class Error private (value: String) extends RespValue {
    override def serialize: Chunk[Byte] =
      ErrorHeader +: encode(value) ++: Terminator
  }
  object Error {
    def apply(value: String): Error =
      new Error(normalized(value)) {}
  }

  /** RESP Integers */
  final case class Integer(value: Long) extends RespValue {
    override def serialize: Chunk[Byte] =
      IntegerHeader +: encode(value) ++: Terminator
  }

  /** RESP Bulk Strings */
  final case class BulkString(value: String) extends RespValue {
    override def serialize: Chunk[Byte] = {
      val encodedData = encode(value)
      val encodedSize = encodedData.size.toLong

      BulkStringHeader +:                      // Header byte
        encode(encodedSize) ++: Terminator ++: // Size of the data
        encodedData ++: Terminator             // Data and the terminator
    }
  }

  /** RESP Arrays */
  final case class Array(items: Chunk[RespValue]) extends RespValue {
    override def serialize: Chunk[Byte] =
      ArrayHeader +:                                      // Header byte
        encode(items.size.toLong) ++: Terminator ++:      // Items count
        items.map(_.serialize).fold(Chunk.empty)(_ ++: _) // Each item serialized
  }
  object Array {
    def apply(items: RespValue*): Array =
      Array(Chunk(items: _*))
  }

  /** RESP Null */
  case object NoValue extends RespValue {
    override def serialize: Chunk[Byte] = {
      val NullCount: Chunk[Byte] = Chunk('-', '1')
      /*
       * Null value representation
       *
       * There is more than one way to serialize a __Null__ value in RESP.
       * Usually the __Null Bulk String__ is used:
       */
      BulkStringHeader +: NullCount ++: Terminator
      /*
       * But for historical reasons there is also __Null Array__ representation:
       * {{{
       *   ArrayHeader +: NullCount ++: Terminator
       * }}}
       */
    }

  }

  /**
   * RESP parser
   */

  /** Parsing error */
  sealed trait ParsingError

  final case class ExcessiveData(remainder: Chunk[Byte]) extends ParsingError
  final case class InvalidInteger(text: String)          extends ParsingError
  final case class InvalidSize(size: Int)                extends ParsingError
  final case class UnknownHeader(byte: Byte)             extends ParsingError
  case object MalformedData                              extends ParsingError
  case object UnexpectedEndOfData                        extends ParsingError

  /** RESP parser */
  def parse(data: Chunk[Byte]): IO[ParsingError, RespValue] = {

    /*
     * Intermediate values
     */

    final case class BasicString(string: String, remainder: Chunk[Byte])
    final case class BasicInt(int: Int, remainder: Chunk[Byte])
    final case class BasicLong(long: Long, remainder: Chunk[Byte])

    /*
     * Helper functions for taking sequence of bytes terminated with a `CRLF`
     *
     * These produce [[BasicString]], [[BasicInt]] and [[BasicLong]] values respectively.
     */

    @tailrec def takeString(data: Chunk[Byte], result: Chunk[Byte] = Chunk.empty): IO[ParsingError, BasicString] =
      data match {
        case CarriageReturn +: LineFeed +: tail => IO.succeed(BasicString(decode(result), tail))
        case byte +: tail                       => takeString(tail, result :+ byte)
        case _                                  => IO.fail(UnexpectedEndOfData)
      }

    def takeInt(data: Chunk[Byte]): IO[ParsingError, BasicInt] =
      for {
        part <- takeString(data)
        int  <- IO.effect(part.string.toInt).orElseFail(InvalidInteger(part.string))
      } yield BasicInt(int, part.remainder)

    def takeLong(data: Chunk[Byte]): IO[ParsingError, BasicLong] =
      for {
        part <- takeString(data)
        long <- IO.effect(part.string.toLong).orElseFail(InvalidInteger(part.string))
      } yield BasicLong(long, part.remainder)

    /* Partial result of parsing */
    final case class ParsedFragment(result: RespValue, remainder: Chunk[Byte])

    /* Parsing process */
    def process(data: Chunk[Byte]): IO[ParsingError, ParsedFragment] = data match {

      case SimpleStringHeader +: tail =>
        for {
          part <- takeString(tail)
        } yield ParsedFragment(SimpleString(part.string), part.remainder)

      case ErrorHeader +: tail =>
        for {
          part <- takeString(tail)
        } yield ParsedFragment(Error(part.string), part.remainder)

      case IntegerHeader +: tail =>
        for {
          part <- takeLong(tail)
        } yield ParsedFragment(Integer(part.long), part.remainder)

      case BulkStringHeader +: tail =>
        def bulk(size: Int, data: Chunk[Byte]): IO[ParsingError, ParsedFragment] = {
          val (result, tail)          = data.splitAt(size)
          val (terminator, remainder) = tail.splitAt(Terminator.size)

          for {
            _ <- IO.fail(UnexpectedEndOfData).when(result.size != size || terminator.size != Terminator.size)
            _ <- IO.fail(MalformedData).when(terminator != Terminator)
          } yield ParsedFragment(BulkString(decode(result)), remainder)
        }

        takeInt(tail).flatMap {
          case BasicInt(-1, remainder)                => IO.succeed(ParsedFragment(NoValue, remainder))
          case BasicInt(size, remainder) if size >= 0 => bulk(size, remainder)
          case BasicInt(other, _)                     => IO.fail(InvalidSize(other))
        }

      case ArrayHeader +: tail =>
        def array(
          count: Int,
          data: Chunk[Byte],
          result: Chunk[RespValue] = Chunk.empty
        ): IO[ParsingError, ParsedFragment] =
          if (count < 1) {
            IO.succeed(ParsedFragment(Array(result), data))
          } else {
            process(data).flatMap { case ParsedFragment(item, remainder) =>
              array(count - 1, remainder, result :+ item)
            }
          }

        takeInt(tail).flatMap {
          case BasicInt(-1, remainder)                => IO.succeed(ParsedFragment(NoValue, remainder))
          case BasicInt(size, remainder) if size >= 0 => array(size, remainder)
          case BasicInt(other, _)                     => IO.fail(InvalidSize(other))
        }

      case header +: _ =>
        IO.fail(UnknownHeader(header))

      case _ =>
        IO.fail(UnexpectedEndOfData)

    }

    process(data).flatMap {
      case ParsedFragment(result, Chunk.empty) => IO.succeed(result)
      case ParsedFragment(_, remainder)        => IO.fail(ExcessiveData(remainder))
    }

  }

}
