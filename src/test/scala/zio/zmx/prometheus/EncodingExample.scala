package zio.zmx.prometheus

import zio._
import zio.console._

object EncodingExample extends zio.App {

  val program = for {
    now    <- clock.instant
    sample <- ZIO.succeed(sampleMetrics(now))
    _      <- putStrLn(sample)
  } yield ()

  private def sampleMetrics(ts: java.time.Instant): String = {
    val labels = Chunk("k1" -> "v1", "k2" -> "v2")

    val c = PMetric
      .incCounter(
        PMetric.counter("myName", "Some Counter Help", labels)
      )
      .get

    val g = PMetric.incGauge(
      PMetric.gauge("myGauge", "Some Gauge Help", labels),
      100
    )

    val h = PMetric.histogram("myHistogram", "Some Histogram Help", labels, PMetric.BucketType.Linear(0, 10, 10)).get

    val s = PMetric
      .summary("mySummary", "Some Summary Help", labels)(
        Quantile(0.2, 0.03).get,
        Quantile(0.5, 0.03).get,
        Quantile(0.9, 0.03).get
      )
      .get

    val s2 = 1.to(100).foldLeft(s) { case (cur, n) =>
      PMetric.observeSummary(cur, n.toDouble, ts)
    }

    PrometheusEncoder.encode(List(c, g, h, s2), ts)

  }

  override def run(args: List[String]): zio.URIO[zio.ZEnv, ExitCode] =
    program.exitCode

}
