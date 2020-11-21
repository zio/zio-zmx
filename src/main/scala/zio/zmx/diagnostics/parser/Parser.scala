package zio.zmx.diagnostics.parser

import zio.zmx.diagnostics.ZMXProtocol
import zio.{ Chunk, IO }

/** Parser for ZMX protocol using RESP (REdis Serialization Protocol) */
object Parser {

  def parse(data: Chunk[Byte]): IO[ZMXProtocol.Error, ZMXProtocol.Request] =
    Resp
      .parse(data)
      .foldM(
        /* Parsing error */
        (parsingError: Resp.ParsingError) => IO.fail(ZMXProtocol.Error.MalformedRequest(parsingError.toString)),
        /* Parsed to RESP value */
        (result: Resp.RespValue) => {

          /*
           * Commands must be sent in RESP format as Array of Bulk Strings
           * First element is obligatory and will be the command name
           * Consecutive elements are optional and will be arguments of the command
           */
          val invalidRequest =
            IO.fail(ZMXProtocol.Error.InvalidRequest("Expected Array of Bulk Strings with at least one element"))

          result match {
            case Resp.Array(items) =>
              /* Collect Bulk Strings only */
              val bulkStrings: Chunk[Resp.BulkString] = items.collect { case bulkString: Resp.BulkString =>
                bulkString
              }

              if (bulkStrings.size != items.size) {
                /* Not all items in Array were Bulk Strings */
                invalidRequest
              } else {
                bulkStrings match {
                  case head +: tail =>
                    val commandName = head.value
                    val arguments   = tail.map(_.value)

                    ZMXProtocol.Command
                      .fromString(commandName)
                      .fold[IO[ZMXProtocol.Error, ZMXProtocol.Request]] {
                        /* Unknown command */
                        IO.fail(ZMXProtocol.Error.UnknownCommand(commandName))
                      } { command =>
                        /* Correct command with optional arguments */
                        IO.succeed {
                          ZMXProtocol.Request(
                            command,
                            arguments.nonEmptyOrElse[Option[Chunk[String]]] {
                              None
                            } {
                              Some(_)
                            }
                          )
                        }
                      }

                  case _ => invalidRequest
                }
              }

            case _ => invalidRequest
          }

        }
      )

  def serialize(response: ZMXProtocol.Response): Chunk[Byte] = response match {
    case ZMXProtocol.Response.Fail(error) =>
      error match {
        case ZMXProtocol.Error.InvalidRequest(error)   => Resp.Error(s"INVALID REQUEST: `$error`!").serialize
        case ZMXProtocol.Error.MalformedRequest(error) => Resp.Error(s"MALFORMED REQUEST: `$error`!").serialize
        case ZMXProtocol.Error.UnknownCommand(command) => Resp.Error(s"UNKNOWN COMMAND: `$command`!").serialize
      }

    case ZMXProtocol.Response.Success(data) =>
      data match {
        case executionMetrics: ZMXProtocol.Data.ExecutionMetrics =>
          // TODO: Format of `ExecutionMetrics` serialized could be discussed and revisited.
          Resp.BulkString(executionMetrics.toString).serialize

        case fiberDump: ZMXProtocol.Data.FiberDump =>
          Resp.Array(fiberDump.dumps.map(Resp.SimpleString.apply)).serialize

        case simple: ZMXProtocol.Data.Simple =>
          Resp.SimpleString(simple.message).serialize
      }
  }

}
