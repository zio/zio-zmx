package zio.zmx.client.backend

import zio.{ Chunk, ExitCode, Schedule, ZEnv, ZIO }
import zio.random.{ nextDoubleBetween, nextIntBetween }
import zio.zmx.metrics._
import zio.duration._
import zio.zmx.state.DoubleHistogramBuckets

object InstrumentedSample {

  // Create a gauge that can be set to absolute values, it can be applied to effects yielding a Double
  val aspGaugeAbs = MetricAspect.setGauge("setGauge")
  // Create a gauge that can be set relative to it's current value, it can be applied to effects yielding a Double
  val aspGaugeRel = MetricAspect.adjustGauge("adjustGauge")

  // Create a histogram with 12 buckets: 0..100 in steps of 10, Infinite
  // It also can be applied to effects yielding a Double
  val aspHistogram =
    MetricAspect.observeHistogram("zmxHistogram", DoubleHistogramBuckets.linear(0.0d, 10.0d, 11).boundaries)

  // Create a summary that can hold 100 samples, the max age of the samples is 1 day.
  // The summary should report th 10%, 50% and 90% Quantile
  // It can be applied to effects yielding an Int
  val aspSummary =
    MetricAspect.observeSummaryWith[Int]("mySummary", 1.day, 100, 0.03d, Chunk(0.1, 0.5, 0.9))(_.toDouble)

  // Create a Set to observe the occurences of unique Strings
  // It can be applied to effects yielding a String
  val aspSet = MetricAspect.occurrences("mySet", "token")

  // Create a counter applicable to any effect
  val aspCountAll    = MetricAspect.count("countAll")
  val aspCountGauges = MetricAspect.count("countGauges")

  private lazy val gaugeSomething = for {
    _ <- nextDoubleBetween(0.0d, 10000.0d) @@ aspGaugeAbs @@ aspCountAll @@ aspCountGauges
    _ <- nextDoubleBetween(-50d, 50d) @@ aspGaugeRel @@ aspCountAll @@ aspCountGauges
  } yield ()

  // Just record something into a histogram
  private lazy val observeNumbers = for {
    _ <- nextDoubleBetween(0.0d, 120.0d) @@ aspHistogram @@ aspCountAll
    _ <- nextIntBetween(100, 500) @@ aspSummary @@ aspCountAll
  } yield ()

  // Observe Strings in order to capture uinque values
  private lazy val observeKey = for {
    _ <- nextIntBetween(10, 20).map(v => s"myKey-$v") @@ aspSet @@ aspCountAll
  } yield ()

  def program: ZIO[ZEnv, Nothing, ExitCode] = for {
    _ <- gaugeSomething.schedule(Schedule.spaced(1000.millis)).forkDaemon
    _ <- observeNumbers.schedule(Schedule.spaced(1000.millis)).forkDaemon
    _ <- observeKey.schedule(Schedule.spaced(1000.millis)).forkDaemon
  } yield ExitCode.success
}
