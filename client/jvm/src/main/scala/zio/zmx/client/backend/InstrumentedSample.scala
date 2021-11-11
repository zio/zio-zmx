package zio.zmx.client.backend

import zio._

object InstrumentedSample {

  private val gaugeCount: Int = 5

  // Create a gauge that can be set to absolute values, it can be applied to effects yielding a Double
  val aspGaugeAbs = (1
    .to(gaugeCount))
    .map { i =>
      (i, ZIOMetric.setGauge("setGauge", MetricLabel("id", s"SubGauge-$i")))
    }
    .toMap

  // Create a gauge that can be set relative to it's current value, it can be applied to effects yielding a Double
  val aspGaugeRel = (1
    .to(gaugeCount))
    .map { i =>
      (i, ZIOMetric.adjustGauge("adjustGauge", MetricLabel("id", s"SubGauge-$i")))
    }
    .toMap

  // Create a histogram with 12 buckets: 0..100 in steps of 10, Infinite
  // It also can be applied to effects yielding a Double
  val aspHistogram =
    ZIOMetric.observeHistogram("zmxHistogram", Chunk(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, Double.MaxValue))

  // Create a summary that can hold 100 samples, the max age of the samples is 1 day.
  // The summary should report th 10%, 50% and 90% Quantile
  // It can be applied to effects yielding an Int
  val aspSummary =
    ZIOMetric.observeSummaryWith[Int]("mySummary", 1.day, 100, 0.03d, Chunk(0.1, 0.5, 0.9))(_.toDouble)

  // Create a Set to observe the occurrences of unique Strings
  // It can be applied to effects yielding a String
  val aspSet = ZIOMetric.occurrences("mySet", "token")

  // Create a counter applicable to any effect
  val aspCountAll    = ZIOMetric.count("countAll")
  val aspCountGauges = ZIOMetric.count("countGauges")

  private lazy val gaugeSomething = for {
    _ <- ZIO.foreach(1.to(gaugeCount)) { i =>
           Random.nextDoubleBetween(0.0d, 1000.0d) @@ aspGaugeAbs(i) @@ aspCountAll @@ aspCountGauges *>
             Random.nextDoubleBetween(-500.0d, 500.0d) @@ aspGaugeRel(i) @@ aspCountAll @@ aspCountGauges
         }
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
