/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.zmx.diagnostics.nio

import java.nio.{ BufferUnderflowException, ByteBuffer => JByteBuffer }

import zio.{ Chunk, IO, UIO }

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
