package zio.zmx.client.frontend.utils

import zio.Chunk
import com.raquo.airstream.split.Splittable

object Implicits {
  implicit val chunkSplittable: Splittable[Chunk] = new Splittable[Chunk] {
    override def map[A, B](inputs: Chunk[A], project: A => B): Chunk[B] =
      inputs.map(project)
  }

  implicit val iterableSplittable: Splittable[Iterable] = new Splittable[Iterable] {
    def map[A, B](inputs: Iterable[A], project: A => B): Iterable[B] = inputs.map(project)
  }

}
