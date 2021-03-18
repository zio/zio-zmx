package zio.zmx.state

import zio.Chunk
import zio.zmx.internal.ScalaCompat._

sealed trait HistogramBuckets {
  def boundaries: Chunk[Double]
  def buckets: Chunk[(Double, Double)] =
    boundaries.map(d => (d, 0d))
}

object HistogramBuckets {

  final case class Manual(limits: Double*) extends HistogramBuckets {
    override def boundaries: Chunk[Double] =
      Chunk.fromArray(limits.toArray.sorted(dblOrdering)) ++ Chunk(Double.MaxValue).distinct
  }

  final case class Linear(start: Double, width: Double, count: Int) extends HistogramBuckets {
    override def boundaries: Chunk[Double] =
      Chunk.fromArray(0.until(count).map(i => start + i * width).toArray) ++ Chunk(Double.MaxValue)
  }

  final case class Exponential(start: Double, factor: Double, count: Int) extends HistogramBuckets {
    override def boundaries: Chunk[Double] =
      Chunk.fromArray(0.until(count).map(i => start * Math.pow(factor, i.toDouble)).toArray) ++ Chunk(Double.MaxValue)
  }
}
