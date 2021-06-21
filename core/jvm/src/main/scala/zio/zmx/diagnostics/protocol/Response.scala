package zio.zmx.diagnostics.protocol

sealed trait Response

object Response {
  final case class Fail(error: Message.Error)  extends Response
  final case class Success(data: Message.Data) extends Response
}
