package zio.zmx.state

import zio.Chunk
import zio.zmx.internal.ScalaCompat._

final case class DoubleHistogramBuckets(buckets: Chunk[(Double, Double)]) {
  def boundaries: Chunk[Double] =
    buckets.map(_._1)
}

object DoubleHistogramBuckets {

  def manual(limits: Double*): DoubleHistogramBuckets = {
    val boundaries = Chunk.fromArray(limits.toArray.sorted(dblOrdering)) ++ Chunk(Double.MaxValue).distinct
    DoubleHistogramBuckets(boundaries.map(boundary => (boundary, 0.0)))
  }

  def linear(start: Double, width: Double, count: Int): DoubleHistogramBuckets = {
    val boundaries = Chunk.fromArray(0.until(count).map(i => start + i * width).toArray) ++ Chunk(Double.MaxValue)
    DoubleHistogramBuckets(boundaries.map(boundary => (boundary, 0.0)))
  }

  def exponential(start: Double, factor: Double, count: Int): DoubleHistogramBuckets = {
    val boundaries =
      Chunk.fromArray(0.until(count).map(i => start * Math.pow(factor, i.toDouble)).toArray) ++ Chunk(Double.MaxValue)
    DoubleHistogramBuckets(boundaries.map(boundary => (boundary, 0.0)))
  }
}
