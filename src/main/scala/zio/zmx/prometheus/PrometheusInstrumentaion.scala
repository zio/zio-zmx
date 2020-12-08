package zio.zmx.prometheus

import zio._
import zio.zmx.metrics._

private[zmx] object PrometheusInstrumentaion {

  def make: ZIO[Any, Nothing, ZMetrics.Service] =
    PrometheusRegistry.make.map(r => new PrometheusInstrumentaion(r))

}

private[zmx] final class PrometheusInstrumentaion private (
  registry: PrometheusRegistry
) extends ZMetrics.Service {

  override def report: ZIO[ZEnv, Nothing, String] = for {
    metrics <- registry.list
    now     <- clock.instant
    encoded  = PrometheusEncoder.encode(metrics, now)
    _       <- console.putStrLn(encoded)
  } yield encoded

  override def increment(
    name: String
  ): ZIO[Any, Nothing, Unit] =
    registry.update[PMetric.Counter](PMetric.counter(name, "", Chunk.empty))(cnt => PMetric.incCounter(cnt))

}
