package zio.zmx.diagnostics

sealed trait ZMXError
case class UnknownZMXCommand(command: String) extends ZMXError
