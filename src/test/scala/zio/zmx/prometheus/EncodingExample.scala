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

    val c = Metric
      .incCounter(
        Metric.counter("myName", "Some Counter Help", labels)
      )
      .get

    val g = Metric.incGauge(
      Metric.gauge("myGauge", "Some Gauge Help", labels),
      100
    )

    val h = Metric.histogram("myHistogram", "Some Histogram Help", labels, Metric.BucketType.Linear(0, 10, 10)).get

    val s = Metric
      .summary("mySummary", "Some Summary Help", labels)(
        Quantile(0.2, 0.03).get,
        Quantile(0.5, 0.03).get,
        Quantile(0.9, 0.03).get
      )
      .get

    val s2 = 1.to(100).foldLeft(s) { case (cur, n) =>
      Metric.observeSummary(cur, n.toDouble, ts)
    }

    PrometheusEncoder.encode(List(c, g, h, s2), ts)

  }

  override def run(args: List[String]): zio.URIO[zio.ZEnv, ExitCode] =
    program.exitCode

}
