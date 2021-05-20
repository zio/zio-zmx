package zio.zmx.statsd

import java.text.DecimalFormat

import zio._
import zio.zmx.Label
import zio.zmx.metrics.{ MetricKey, MetricListener }

object StatsdListener {

  def make =
    ZIO.service[StatsdClient.StatsdClientSvc].map(new StatsdListener(_) {})

}

sealed abstract class StatsdListener(client: StatsdClient.StatsdClientSvc) extends MetricListener {

  override def setGauge(key: MetricKey.Gauge, value: Double): UIO[Unit] = report(key, value)

  override def setCounter(key: MetricKey.Counter, value: Double): UIO[Unit] = report(key, value)

  override def observeHistogram(key: MetricKey.Histogram, value: Double): UIO[Unit] = report(key, value)

  override def observeSummary(key: MetricKey.Summary, value: Double): UIO[Unit] = report(key, value)

  private def report(key: MetricKey, v: Double) =
    encode(key, v)
      .foldM(
        _ => ZIO.unit,
        s =>
          (client.write(s).flatMap(l => ZIO.succeed(println(s"Wrote [$l] bytes")))).catchAll(t => ZIO.succeed(t.printStackTrace()))
      )

  def encode(key: MetricKey, v: Double): ZIO[Any, Unit, String] = key match {
    //  TODO: Need to reset counters for statsd after reporting
    case ck: MetricKey.Counter   => encode(ck.name, v, "c", ck.tags)
    case gk: MetricKey.Gauge     => encode(gk.name, v, "g", gk.tags)
    case hk: MetricKey.Histogram =>
      encode(hk.name, v, "g", hk.tags) // Histograms need to be configured in the statsd agent
    case sk: MetricKey.Summary   =>
      encode(sk.name, v, "g", sk.tags) // Summaries need to be configured in the statsd agent
    case _                       => ZIO.fail(())
  }

  private def encode(
    name: String,
    value: Double,
    metricType: String,
    tags: Seq[Label]
  ): ZIO[Any, Unit, String] = Task {
    val tagString = encodeTags(tags)
    s"${name}:${format.format(value)}|${metricType}${tagString}"
  }.catchAll(_ => ZIO.fail(()))

  private def encodeTags(tags: Seq[Label]): String =
    if (tags.isEmpty) ""
    else tags.map(t => s"${t._1}:${t._2}").mkString("|#", ",", "")

  private lazy val format = new DecimalFormat("0.################")

}
