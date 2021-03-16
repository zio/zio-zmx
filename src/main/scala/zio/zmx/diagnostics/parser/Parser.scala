package zio.zmx.diagnostics.parser

import zio.zmx.diagnostics.protocol._
import zio.{ Chunk, IO }

/** Parser for ZMX protocol using RESP (REdis Serialization Protocol) */
object Parser {

  def parse(data: Chunk[Byte]): IO[Message.Error, Request] =
    Resp
      .parse(data)
      .foldM(
        /* Parsing error */
        (parsingError: Resp.ParsingError) => IO.fail(Message.Error.MalformedRequest(parsingError.toString)),
        /* Parsed to RESP value */
        (result: Resp.RespValue) => {

          /*
           * Commands must be sent in RESP format as Array of Bulk Strings
           * First element is obligatory and will be the command name
           * Consecutive elements are optional and will be arguments of the command
           */
          val invalidRequest =
            IO.fail(Message.Error.InvalidRequest("Expected Array of Bulk Strings with at least one element"))

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

                    Message.Command
                      .fromString(commandName)
                      .fold[IO[Message.Error, Request]] {
                        /* Unknown command */
                        IO.fail(Message.Error.UnknownCommand(commandName))
                      } { command =>
                        /* Correct command with optional arguments */
                        IO.succeed {
                          Request(
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

  def serialize(response: Response): Chunk[Byte] = response match {
    case Response.Fail(error) =>
      error match {
        case Message.Error.InvalidRequest(error)   => Resp.Error(s"INVALID REQUEST: `$error`!").serialize
        case Message.Error.MalformedRequest(error) => Resp.Error(s"MALFORMED REQUEST: `$error`!").serialize
        case Message.Error.UnknownCommand(command) => Resp.Error(s"UNKNOWN COMMAND: `$command`!").serialize
      }

    case Response.Success(data) =>
      data match {
        case executionMetrics: Message.Data.ExecutionMetrics =>
          // TODO: Format of `ExecutionMetrics` serialized could be discussed and revisited. See: https://github.com/zio/zio-zmx/issues/142
          Resp.BulkString(executionMetrics.toString).serialize

        case fiberDump: Message.Data.FiberDump =>
          Resp.Array(fiberDump.dumps.map(Resp.BulkString)).serialize

        case simple: Message.Data.Simple =>
          Resp.SimpleString(simple.message).serialize
      }
  }

}
