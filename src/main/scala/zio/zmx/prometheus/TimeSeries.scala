package zio.zmx.prometheus

import zio.Chunk

final case class TimeSeries(
  maxAge: java.time.Duration = TimeSeries.defaultMaxAge,
  maxSize: Int = TimeSeries.defaultMaxSize,
  samples: Chunk[(Double, java.time.Instant)] = Chunk.empty
) {
  def observe(v: Double, t: java.time.Instant): TimeSeries =
    copy(samples =
      if (samples.length == maxSize) filterSamples(t, maxAge).take(maxSize - 1) ++ Chunk((v, t))
      else samples ++ Chunk((v, t))
    )

  def timedSamples(i: java.time.Instant, t: Option[java.time.Duration]): Chunk[(Double, java.time.Instant)] =
    filterSamples(i, t.getOrElse(maxAge))

  private def filterSamples(t: java.time.Instant, d: java.time.Duration): Chunk[(Double, java.time.Instant)] =
    samples.filter(_._2.toEpochMilli >= t.toEpochMilli - d.toMillis)
}

object TimeSeries {
  val defaultMaxAge: java.time.Duration = java.time.Duration.ofHours(1)
  val defaultMaxSize: Int               = 1024
}
