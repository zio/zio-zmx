package zio.zmx.example

import zio._
import zio.random._
import zio.duration._
import zio.zmx.metrics._
import zio.zmx.state.DoubleHistogramBuckets

trait InstrumentedSample {

  // Create a gauge that can be set to absolute values, it can be applied to effects yielding a Double
  val aspGaugeAbs = MetricAspect.gauge[Double](MetricKey.Gauge("setGauge"))(ZIO.succeed(_))
  // Create a gauge that can be set relative to it's current value, it can be applied to effects yielding a Double
  val aspGaugeRel = MetricAspect.gaugeRelative[Double](MetricKey.Gauge("adjustGauge"))(ZIO.succeed(_))

  // Create a histogram with 12 buckets: 0..100 in steps of 10, Infinite
  // It also can be applied to effects yielding a Double
  val aspHistogram =
    MetricAspect.observeInHistogram[Double](
      MetricKey.Histogram("zmxHistogram", DoubleHistogramBuckets.linear(0.0d, 10.0d, 11).boundaries)
    )(ZIO.succeed(_))

  // Create a summary that can hold 100 samples, the max age of the samples is 1 day.
  // The summary should report th 10%, 50% and 90% Quantile
  // It can be applied to effects yielding an Int
  val aspSummary =
    MetricAspect.observeInSummary[Int](MetricKey.Summary("mySummary", 1.day, 100, 0.03d, Chunk(0.1, 0.5, 0.9)))(i =>
      ZIO.succeed(i.doubleValue())
    )

  // Create a Set to observe the occurences of unique Strings
  // It can be applied to effects yielding a String
  val aspSet = MetricAspect.observeString[String](MetricKey.SetCount("mySet", "token"))(ZIO.succeed(_))

  // Create a counter applicable to any effect
  val aspCountAll = MetricAspect.count(MetricKey.Counter("countAll"))

  private lazy val gaugeSomething = for {
    _ <- nextDoubleBetween(0.0d, 100.0d) @@ aspGaugeAbs @@ aspCountAll
    _ <- nextDoubleBetween(-50d, 50d) @@ aspGaugeRel @@ aspCountAll
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
    _ <- gaugeSomething.schedule(Schedule.spaced(200.millis)).forkDaemon
    _ <- observeNumbers.schedule(Schedule.spaced(150.millis)).forkDaemon
    _ <- observeKey.schedule(Schedule.spaced(300.millis)).forkDaemon
  } yield ExitCode.success
}
