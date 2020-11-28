package zio.zmx.prometheus

import zio.Chunk

final case class TimeSeries(
  maxAge: java.time.Duration,
  maxSize: Int,
  samples: Chunk[(Double, java.time.Instant)] = Chunk.empty
) {
  def observe(v: Double, t: java.time.Instant): TimeSeries = {
    val filtered = filterSamples(t, maxAge)
    copy(samples =
      if (filtered.length == maxSize) samples.take(maxSize - 1) ++ Chunk((v, t)) else filtered ++ Chunk((v, t))
    )
  }

  def timedSamples(i: java.time.Instant, t: Option[java.time.Duration]): Chunk[Double] =
    filterSamples(i, t.getOrElse(maxAge)).map(_._1)

  private def filterSamples(t: java.time.Instant, d: java.time.Duration): Chunk[(Double, java.time.Instant)] =
    samples.dropWhile(_._2.toEpochMilli < t.toEpochMilli - d.toMillis)
}

object TimeSeries {
  val defaultMaxAge: java.time.Duration = java.time.Duration.ofHours(1)
  val defaultMaxSize: Int               = 1024
}
