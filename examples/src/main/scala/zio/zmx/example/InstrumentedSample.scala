package zio.zmx.example

import zio._
import zio.random._
import zio.duration._
import zio.zmx.metrics._
import zio.zmx.state.DoubleHistogramBuckets

trait InstrumentedSample {

  val aspGaugeAbs = MetricAspect.gauge[Double](MetricKey.Gauge("setGauge"))(ZIO.succeed(_))
  val aspGaugeRel = MetricAspect.gaugeRelative[Double](MetricKey.Gauge("adjustGauge"))(ZIO.succeed(_))

  val aspHistogram =
    MetricAspect.observeInHistogram[Double](
      MetricKey.Histogram("myHistogram", DoubleHistogramBuckets.linear(0.0d, 10.0d, 11).boundaries)
    )(ZIO.succeed(_))

  val aspSummary =
    MetricAspect.observeInSummary[Double](MetricKey.Summary("mySummary", 1.day, 100, 0.03d, Chunk(0.1, 0.5, 0.9)))(
      ZIO.succeed(_)
    )

  val aspSet = MetricAspect.observeString[String](MetricKey.SetCount("mySet", "token"))(ZIO.succeed(_))

  val aspCountAll = MetricAspect.count(MetricKey.Counter("countAll"))

  // Manipulate an arbitrary Gauge
  private lazy val gaugeSomething = for {
    _ <- nextDoubleBetween(0.0d, 100.0d) @@ aspGaugeAbs @@ aspCountAll
    _ <- nextDoubleBetween(-50d, 50d) @@ aspGaugeRel @@ aspCountAll
  } yield ()

  // Just record something into a histogram
  private lazy val observeHistograms = for {
    _ <- nextDoubleBetween(0.0d, 100.0d) @@ aspHistogram @@ aspCountAll
    _ <- nextDoubleBetween(100d, 500d) @@ aspSummary @@ aspCountAll
  } yield ()

  // Observe Strings in order to capture uinque values
  private lazy val observeKey = for {
    _ <- nextIntBetween(10, 20).map(v => s"myKey-$v") @@ aspSet
  } yield ()

  def program: ZIO[ZEnv, Nothing, ExitCode] = for {
    _ <- gaugeSomething.schedule(Schedule.spaced(200.millis)).forkDaemon
    _ <- observeHistograms.schedule(Schedule.spaced(150.millis)).forkDaemon
    _ <- observeKey.schedule(Schedule.spaced(300.millis)).forkDaemon
  } yield ExitCode.success
}
