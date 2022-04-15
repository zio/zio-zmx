package zio.zmx.newrelic

import zio._
import zio.metrics._
import zio.stream._

object MetricPlayground extends ZIOAppDefault {

  def run = {

    val aspSummary =
      Metric
        .summary("summary", 1.day, 100, 0.03d, Chunk(0.1d, 0.5d, 0.9d, 1.0d))
        .tagged(MetricLabel("id", "mySummary"))
        .contramap[Int](_.toDouble)

    val aspHistogram =
      Metric
        .histogram("histogram", MetricKeyType.Histogram.Boundaries.linear(0, 10, 11))
        .tagged(MetricLabel("id", "myHistogram"))
        .contramap[Int](_.toDouble)

    val stream = ZStream(5, 8, 3, 1, 2, 4, 6, 7, 9)

    val instrumented = stream.mapZIO(i => ZIO.succeed(i) @@ aspSummary @@ aspHistogram)

    val pgm1 = for {
      _         <- instrumented.runDrain
      summary   <- aspSummary.value
      histogram <- aspHistogram.value
      _         <- Console.printLine(s"Summary:   $summary")
      _         <- Console.printLine(s"Histogram: $histogram")
    } yield ()

    val config = NewRelicConfig(10000, "")

    // val pgm2

    pgm1
  }

}
