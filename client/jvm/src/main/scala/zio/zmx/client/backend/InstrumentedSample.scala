package zio.zmx.client.backend

import zio._
import zio.metrics._

object InstrumentedSample {

  // Create a gauge that can be set to absolute values, it can be applied to effects yielding a Double
  val aspGaugeAbs = ZIOMetric.setGauge("setGauge")
  // Create a gauge that can be set relative to it's current value, it can be applied to effects yielding a Double
  val aspGaugeRel = ZIOMetric.adjustGauge("adjustGauge")

  // Create a histogram with 12 buckets: 0..100 in steps of 10, Infinite
  // It also can be applied to effects yielding a Double
  val aspHistogram =
    ZIOMetric.observeHistogram("zmxHistogram", ZIOMetric.Histogram.Boundaries.linear(0, 10, 11))

  // Create a summary that can hold 100 samples, the max age of the samples is 1 day.
  // The summary should report th 10%, 50% and 90% Quantile
  // It can be applied to effects yielding an Int
  val aspSummary =
    ZIOMetric.observeSummaryWith[Int]("mySummary", 1.day, 100, 0.03d, Chunk(0.1, 0.5, 0.9))(_.toDouble)

  // Create a Set to observe the occurences of unique Strings
  // It can be applied to effects yielding a String
  val aspSet = ZIOMetric.occurrences("mySet", "token")

  // Create a counter applicable to any effect
  val aspCountAll    = ZIOMetric.count("countAll")
  val aspCountGauges = ZIOMetric.count("countGauges")

  private lazy val gaugeSomething = for {
    _ <- Random.nextDoubleBetween(0.0d, 10000.0d) @@ aspGaugeAbs @@ aspCountAll @@ aspCountGauges
    _ <- Random.nextDoubleBetween(-50d, 50d) @@ aspGaugeRel @@ aspCountAll @@ aspCountGauges
  } yield ()

  // Just record something into a histogram
  private lazy val observeNumbers = for {
    _ <- Random.nextDoubleBetween(0.0d, 120.0d) @@ aspHistogram @@ aspCountAll
    _ <- Random.nextIntBetween(100, 500) @@ aspSummary @@ aspCountAll
  } yield ()

  // Observe Strings in order to capture uinque values
  private lazy val observeKey = for {
    _ <- Random.nextIntBetween(10, 20).map(v => s"myKey-$v") @@ aspSet @@ aspCountAll
  } yield ()

  def program: ZIO[ZEnv, Nothing, Unit] = for {
    _ <- gaugeSomething.schedule(Schedule.spaced(1000.millis)).forkDaemon
    _ <- observeNumbers.schedule(Schedule.spaced(1000.millis)).forkDaemon
    _ <- observeKey.schedule(Schedule.spaced(1000.millis)).forkDaemon
  } yield ()
}
