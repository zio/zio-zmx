package zio.zmx.server

sealed trait ZMXError
case class UnknownZMXCommand(command: String) extends ZMXError
