package zio.zmx.diagnostics

import zio._

package object parser {
  
  type ZMXParser = Has[ZMXParser.Service]

  object ZMXParser {
    
    trait Service {
      def asString(message: ZMXProtocol.Message, replyType: ZMXServerResponse): String
      def fromString(command: String): IO[UnknownZMXCommand, ZMXProtocol.Command]
    }

    val respParser: Layer[Nothing, ZMXParser] = ZLayer.succeed(
      new Service {
        def asString(message: ZMXProtocol.Message, replyType: ZMXServerResponse): String = 
          RespZMXParser.asString(message, replyType)

        def fromString(command: String): IO[UnknownZMXCommand, ZMXProtocol.Command] = 
          RespZMXParser.fromString(command)
      }
    )

    def asString(message: ZMXProtocol.Message, replyType: ZMXServerResponse): URIO[ZMXParser, String] =
      ZIO.access(_.get.asString(message, replyType))

    def fromString(command: String): ZIO[ZMXParser, UnknownZMXCommand, ZMXProtocol.Command] = 
      ZIO.accessM(_.get.fromString(command))
  }

}
