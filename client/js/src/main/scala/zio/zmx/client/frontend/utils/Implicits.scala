package zio.zmx.client.frontend.utils

import zio.Chunk
import com.raquo.airstream.split.Splittable

object Implicits {
  type IntkeyMap[V] = Map[Int, V]

  implicit val chunkSplittable: Splittable[Chunk] = new Splittable[Chunk] {
    override def map[A, B](inputs: Chunk[A], project: A => B): Chunk[B] =
      inputs.map(project)
  }

}
