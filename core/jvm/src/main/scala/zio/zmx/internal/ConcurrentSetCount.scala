package zio.zmx.internal

import java.util.concurrent.atomic.LongAdder
import java.util.concurrent.ConcurrentHashMap

import zio.{ Chunk, ChunkBuilder }

sealed abstract class ConcurrentSetCount {

  def count(): Long

  def observe(word: String): Unit

  def snapshot(): Chunk[(String, Long)]

}

object ConcurrentSetCount {

  def manual(): ConcurrentSetCount =
    new ConcurrentSetCount {
      private val count0 = new LongAdder
      private val values = new ConcurrentHashMap[String, LongAdder]

      def count(): Long = count0.longValue()

      def observe(word: String): Unit = {
        count0.increment()
        var slot = values.get(word)
        if (slot eq null) {
          val cnt = new LongAdder
          values.putIfAbsent(word, cnt)
          slot = values.get(word)
        }
        slot match {
          case la: LongAdder =>
            la.increment()
          case null          =>
        }
      }

      def snapshot(): Chunk[(String, Long)] = {
        val builder = ChunkBuilder.make[(String, Long)]()
        val it      = values.entrySet().iterator()
        while (it.hasNext()) {
          val e = it.next()
          builder += e.getKey() -> e.getValue().longValue()
        }

        builder.result()
      }
    }
}
