package zio.zmx.diagnostics.protocol

import zio.Chunk

final case class Request(
  command: Message.Command,
  args: Option[Chunk[String]]
)
