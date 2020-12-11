package zio.zmx.prometheus

import zio._
import zio.stm._

object PrometheusRegistry {

  def make = TMap.empty[String, PMetric].commit.map(i => new PrometheusRegistry(i))
}

final class PrometheusRegistry private (
  items: TMap[String, PMetric]
) {

  def update(zero: PMetric)(f: PMetric => Option[PMetric]) = (for {
    om <- createOrGet(zero)
    _  <- om match {
            case None    => ZSTM.unit
            case Some(m) => doUpdate(f(m))
          }
  } yield ()).commit

  private def doUpdate(v: Option[PMetric]) =
    v match {
      case None    => ZSTM.succeed(())
      case Some(m) => items.put(m.registryKey, m)
    }

  def list: ZIO[Any, Nothing, List[PMetric]] = items.values.commit

  private def createOrGet(
    zero: PMetric
  ): ZSTM[Any, Nothing, Option[PMetric]] = for {
    name <- ZSTM.succeed(zero.registryKey)
    pm   <- items.getOrElse(name, zero)
    r     = if (pm.details.getClass == zero.getClass) Some(pm) else None
  } yield r

}
