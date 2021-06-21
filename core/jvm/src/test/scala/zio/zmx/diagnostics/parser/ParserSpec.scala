package zio.zmx.diagnostics.parser

import java.nio.charset.StandardCharsets.UTF_8

import zio.Chunk
import zio.internal.ExecutionMetrics
import zio.test.Assertion._
import zio.test._
import zio.zmx.diagnostics.protocol._

object ParserSpec extends DefaultRunnableSpec {

  /** Helper for creating `Chunk[Byte]` from `String` */
  private def bytes(value: String): Chunk[Byte] =
    Chunk.fromArray(value.getBytes(UTF_8))

  override def spec =
    suite("ParserSpec")(
      suite("Serialization")(
        /** `Response.Success` */
        test("Serializing a `Data.FiberDump` successful response") {
          assert {
            val dumps = Chunk(
              "{fiber dump 1}",
              "{fiber dump 2}",
              "{fiber dump 3}"
            )
            Parser.serialize(Response.Success(Message.Data.FiberDump(dumps)))
          } {
            equalTo(bytes("*3\r\n$14\r\n{fiber dump 1}\r\n$14\r\n{fiber dump 2}\r\n$14\r\n{fiber dump 3}\r\n"))
          }
        },
        test("Serializing a `Data.ExecutionMetrics` successful response") {
          assert {
            val metrics = new ExecutionMetrics {
              override def concurrency: Int    = 1
              override def capacity: Int       = 2
              override def size: Int           = 3
              override def enqueuedCount: Long = 4
              override def dequeuedCount: Long = 5
              override def workersCount: Int   = 6
            }
            Parser.serialize(Response.Success(Message.Data.ExecutionMetrics(metrics)))
          } {
            equalTo(
              bytes(
                "$86\r\nconcurrency:1\r\ncapacity:2\r\nsize:3\r\nenqueued_count:4\r\ndequeued_count:5\r\nworkers_count:6\r\n"
              )
            )
          }
        },
        test("Serializing a `Data.Simple` successful response") {
          assert {
            Parser.serialize(Response.Success(Message.Data.Simple("This is a TEST")))
          } {
            equalTo(bytes("+This is a TEST\r\n"))
          }
        },
        /** `Response.Fail` */
        test("Serializing a `Error.InvalidRequest` error response") {
          assert {
            Parser.serialize(Response.Fail(Message.Error.InvalidRequest("Message")))
          } {
            equalTo(bytes("-INVALID REQUEST: `Message`!\r\n"))
          }
        },
        test("Serializing a `Error.MalformedRequest` error response") {
          assert {
            Parser.serialize(Response.Fail(Message.Error.MalformedRequest("Message")))
          } {
            equalTo(bytes("-MALFORMED REQUEST: `Message`!\r\n"))
          }
        },
        test("Serializing a `Error.UnknownCommand` error response") {
          assert {
            Parser.serialize(Response.Fail(Message.Error.UnknownCommand("Command")))
          } {
            equalTo(bytes("-UNKNOWN COMMAND: `Command`!\r\n"))
          }
        }
      ),
      suite("Parsing")(
        /** Correct payloads */
        testM("Parsing a fiber dump request") {
          Parser.parse(bytes("*1\r\n$4\r\ndump\r\n")).map {
            assert(_) {
              equalTo(
                Request(
                  command = Message.Command.FiberDump,
                  args = None
                )
              )
            }
          }
        },
        testM("Parsing a execution metrics request") {
          Parser.parse(bytes("*1\r\n$7\r\nmetrics\r\n")).map {
            assert(_) {
              equalTo(
                Request(
                  command = Message.Command.ExecutionMetrics,
                  args = None
                )
              )
            }
          }
        },
        testM("Parsing a test request") {
          Parser.parse(bytes("*1\r\n$4\r\ntest\r\n")).map {
            assert(_) {
              equalTo(
                Request(
                  command = Message.Command.Test,
                  args = None
                )
              )
            }
          }
        },
        testM("Parsing a fiber dump request with arguments") {
          Parser.parse(bytes("*4\r\n$4\r\ndump\r\n$2\r\nA1\r\n$2\r\nA2\r\n$2\r\nA3\r\n")).map {
            assert(_) {
              equalTo(
                Request(
                  command = Message.Command.FiberDump,
                  args = Some(Chunk("A1", "A2", "A3"))
                )
              )
            }
          }
        },
        /** Incorrect payloads */
        testM("Parsing a request with unknown command") {
          Parser.parse(bytes("*1\r\n$7\r\nunknown\r\n")).flip.map {
            assert(_) {
              equalTo(
                Message.Error.UnknownCommand("unknown")
              )
            }
          }
        },
        testM("Parsing a invalid request") {
          Parser.parse(bytes("-That's not an array :O\r\n")).flip.map {
            assert(_) {
              equalTo(
                Message.Error.InvalidRequest("Expected Array of Bulk Strings with at least one element")
              )
            }
          }
        },
        testM("Parsing a request with empty Array") {
          Parser.parse(bytes("*0\r\n")).flip.map {
            assert(_) {
              equalTo(
                Message.Error.InvalidRequest("Expected Array of Bulk Strings with at least one element")
              )
            }
          }
        },
        testM("Parsing a request with Array with not a Bulk Strings") {
          Parser.parse(bytes("*1\r\n+That's really not a Bulk String :/\r\n")).flip.map {
            assert(_) {
              equalTo(
                Message.Error.InvalidRequest("Expected Array of Bulk Strings with at least one element")
              )
            }
          }
        },
        testM("Parsing a empty request") {
          Parser.parse(bytes("")).flip.map {
            assert(_) {
              equalTo(
                Message.Error.MalformedRequest("UnexpectedEndOfData")
              )
            }
          }
        },
        testM("Parsing a broken request") {
          Parser.parse(bytes("???")).flip.map {
            assert(_) {
              equalTo(
                Message.Error.MalformedRequest("UnknownHeader(63)")
              )
            }
          }
        }
      )
    )

}
