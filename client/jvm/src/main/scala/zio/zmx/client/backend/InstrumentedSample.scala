package zio.zmx.client.backend

import zio._
import zio.metrics._

object InstrumentedSample {

  private val gaugeCount: Int = 5

  // Create a gauge that can be set to absolute values, it can be applied to effects yielding a Double
  val aspGaugeAbs = (1
    .to(gaugeCount))
    .map { i =>
      (i, Metric.gauge("setGauge").tagged(MetricLabel("id", s"SubGauge-$i")))
    }
    .toMap

  // Create a histogram with 12 buckets: 0..100 in steps of 10, Infinite
  // It also can be applied to effects yielding a Double
  val aspHistogram =
    Metric
      .histogram("histogram", MetricKeyType.Histogram.Boundaries.linear(0, 10, 11))
      .tagged(MetricLabel("id", "myHistogram"))

  // Create a summary that can hold 100 samples, the max age of the samples is 1 day.
  // The summary should report th 10%, 50% and 90% Quantile
  // It can be applied to effects yielding an Int
  val aspSummary =
    Metric
      .summary("summary", 1.day, 100, 0.03d, Chunk(0.1d, 0.5d, 0.9d))
      .tagged(MetricLabel("id", "mySummary"))
      .contramap[Int](_.toDouble)

  // Create a Set to observe the occurrences of unique Strings
  // It can be applied to effects yielding a String
  val aspFrequency =
    Metric
      .frequency("frequency")
      .tagged(MetricLabel("id", "myFrequency"))

  // Create a counter applicable to any effect
  val aspCountAll    = Metric.counter("countAll").contramap[Any](_ => 1L)
  val aspCountGauges = Metric.counter("countGauges").contramap[Any](_ => 1L)

  private lazy val gaugeSomething =
    ZIO.foreach(1.to(gaugeCount)) { i =>
      Random.nextDoubleBetween(0.0d, 1000.0d) @@ aspGaugeAbs(i) @@ aspCountAll @@ aspCountGauges
    }

  // Just record something into a histogram
  private lazy val observeNumbers =
    (Random.nextDoubleBetween(0.0d, 120.0d) @@ aspHistogram @@ aspCountAll)
      .zipPar(Random.nextIntBetween(100, 500) @@ aspSummary @@ aspCountAll)
      .unit

  // Observe Strings in order to capture uinque values
  private lazy val observeKey = Random.nextIntBetween(10, 20).map(v => s"myKey-$v") @@ aspFrequency @@ aspCountAll

  def program: ZIO[ZEnv, Nothing, Unit] = for {
    _ <- gaugeSomething.schedule(Schedule.spaced(1000.millis)).forkDaemon
    _ <- observeNumbers.schedule(Schedule.spaced(1000.millis)).forkDaemon
    _ <- observeKey.schedule(Schedule.spaced(1000.millis)).forkDaemon
  } yield ()
}
