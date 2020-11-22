package zio.zmx.prometheus

import zio._

object App extends zio.App {
  val program = for {
    _     <- console.putStrLn("Hello there!")
    labels = Map[String, String]("k1" -> "v1", "k2" -> "v2")
    c     <- ZIO(Metric.counter("myName", labels))
    g     <- ZIO(Metric.gauge("myGauge", labels))
    h     <- ZIO(Metric.histogram("myHistogram", labels, Metric.BucketType.Linear(0, 10, 10)))
    //s     <- ZIO(Metric.summary("mySummary", labels, 10, Metric.Quantile(10, 0.5)))
    _     <- console.putStrLn(PrometheusEncoder.encode(List[Metric](c, g, h), None))
  } yield ()

  override def run(args: List[String]): zio.URIO[zio.ZEnv, ExitCode] =
    program.exitCode

}
