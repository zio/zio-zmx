package zio.zmx.prometheus

import zio.Chunk

sealed abstract class Quantile private (
  val phi: Double,  // The quantile
  val error: Double // The error margin
) {
  override def toString(): String = s"Quantile($phi, $error)"
}

object Quantile extends WithDoubleOrdering {
  def apply(phi: Double, error: Double): Option[Quantile] =
    if (phi >= 0 && phi <= 1 && error >= 0 && error <= 1) Some(new Quantile(phi, error) {}) else None

  def calculateQuantiles(
    samples: Chunk[Double],
    quantiles: Chunk[Quantile]
  ): Chunk[(Quantile, Option[Double])] = {

    // The number of the samples examined
    val sampleCnt     = samples.size
    // We need the quantiles sorted
    val sortedQs      = quantiles.sortBy(_.phi)(dblOrdering)
    // We also need the samples sorted
    val sortedSamples = samples.sorted(dblOrdering)

    def get(current: Option[Double], consumed: Int, q: Quantile, rest: Chunk[Double]): ResolvedQuantile =
      rest match {
        case c if c.isEmpty     => ResolvedQuantile(q, None, consumed, Chunk.empty)
        case c if q.phi == 1.0d => ResolvedQuantile(q, Some(c.max(dblOrdering)), consumed + c.length, Chunk.empty)
        case c                  =>
          // Split in 2 chunks, the first chunk contains all elements of the same value as the chunk head
          val sameHead     = c.splitWhere(_ > c.head)
          // How many elements do we want to accept for this quantile
          val desired      = q.phi * sampleCnt
          // The error margin
          val allowedError = q.error / 2 * desired
          // Taking into account the elements consumed from the samples so far and the number of
          // same elements at the beginning of the chunk
          // calculate the number of elements we would have if we selected the current head as result
          val candConsumed = consumed + sameHead._1.length

          // If we haven't got enough elements yet, recurse
          if (candConsumed < desired - allowedError) get(c.headOption, consumed + sameHead._1.length, q, sameHead._2)
          // If we have too many elements, select the previous value and hand back the the rest as leftover
          else if (candConsumed > desired + allowedError) ResolvedQuantile(q, current, consumed, c)
          // If we are in the target interval, select the current head and hand back the leftover after dropping all elements
          // from the sample chunk that are equal to the current head
          else ResolvedQuantile(q, current, consumed, c)
      }

    val resolved = sortedQs match {
      case e if e.isEmpty => Chunk.empty
      case c              =>
        sortedQs.tail.foldLeft(Chunk(get(None, 0, c.head, sortedSamples))) { case (cur, q) =>
          Chunk(get(cur.head.value, cur.head.consumed, q, cur.head.rest)) ++ cur
        }
    }

    resolved.map(rq => (rq.quantile, rq.value))
  }

  private case class ResolvedQuantile(
    quantile: Quantile,    // The Quantile that sahll be resolved
    value: Option[Double], // Some(d) if a value for the quantile could be found, None otherwise
    consumed: Int,         // How many samples have been consumed before this quantile
    rest: Chunk[Double]    // The rest of the samples after the quantile has been resolved
  )

}
