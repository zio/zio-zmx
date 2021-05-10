package zio.zmx.internal

import zio.Chunk

import java.util.concurrent.atomic.{ AtomicReferenceArray, LongAdder }
import zio.ChunkBuilder

sealed abstract class ConcurrentHistogram {

  // The overall count for all observed values in the histogram
  def count(): Long

  // Observe a single value
  def observe(value: Double): Unit

  // Create a Snaphot (Boundary, Sum of all observed values for the bucket with that boundary)
  def snapshot(): Chunk[(Double, Double)]

  // The sum of all observed values
  def sum(): Double
}

object ConcurrentHistogram {

  def manual(bounds: Chunk[Double]): ConcurrentHistogram =
    new ConcurrentHistogram {
      private[this] val values     = new AtomicReferenceArray[Double](bounds.length + 1)
      private[this] val boundaries = Array.ofDim[Double](bounds.length)
      private[this] val count      = new LongAdder
      private[this] val size       = bounds.length
      bounds.sorted.zipWithIndex.foreach { case (n, i) => boundaries(i) = n }

      def count(): Long            =
        count.longValue

      // Insert the value into the right bucket with a binary search
      def observe(value: Double): Unit = {
        var from = 0
        var to   = size
        while (from != to) {
          val mid      = from + (to - from) / 2
          val boundary = boundaries(mid)
          if (value <= boundary) to = mid else from = mid
        }
        count.increment()
        values.getAndUpdate(from, _ + value)
        ()
      }

      def snapshot(): Chunk[(Double, Double)] = {
        val builder = ChunkBuilder.make[(Double, Double)]()
        var i       = 0
        while (i != size + 1) {
          val boundary = boundaries(i)
          val value    = values.get(i)
          builder += boundary -> value
          i += 1
        }
        builder.result()
      }

      def sum(): Double = {
        var i   = 0
        var sum = 0.0
        while (i != size) {
          sum += values.get(i)
          i += 1
        }
        sum
      }
    }
}
