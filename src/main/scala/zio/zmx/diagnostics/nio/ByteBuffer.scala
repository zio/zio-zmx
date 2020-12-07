package zio.zmx.diagnostics.nio

import java.nio.{BufferUnderflowException, ByteBuffer => JByteBuffer}

import zio.{Chunk, IO, UIO}

class ByteBuffer(val buffer: JByteBuffer) {

  def clear: UIO[Unit] = IO.effectTotal(buffer.clear()).unit

  def flip: UIO[Unit] = IO.effectTotal(buffer.flip()).unit

  def getChunk(maxLength: Int = Int.MaxValue): IO[BufferUnderflowException, Chunk[Byte]] =
    IO.effect {
      val array = Array.ofDim[Byte](math.min(maxLength, buffer.remaining()))
      buffer.get(array)
      Chunk.fromArray(array)
    }.refineToOrDie[BufferUnderflowException]
}
object ByteBuffer {

  def byte(capacity: Int): IO[IllegalArgumentException, ByteBuffer] =
    IO.effect(JByteBuffer.allocate(capacity))
      .map(new ByteBuffer(_))
      .refineToOrDie[IllegalArgumentException]

  def byte(chunk: Chunk[Byte]): IO[Nothing, ByteBuffer] =
    IO.effectTotal(JByteBuffer.wrap(chunk.toArray)).map(new ByteBuffer(_))
}