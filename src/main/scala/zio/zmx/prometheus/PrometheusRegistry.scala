package zio.zmx.prometheus

import zio._
import zio.stm._

object PrometheusRegistry {

  def make = TMap.empty[String, PMetric].commit.map(i => new PrometheusRegistry(i))
}

final class PrometheusRegistry private (
  items: TMap[String, PMetric]
) {

  def update(key: String, zero: PMetric)(f: PMetric => Option[PMetric]) = (for {
    om <- createOrGet(key, zero)
    _  <- om match {
            case None    => ZSTM.unit
            case Some(m) => doUpdate(key, f(m))
          }
  } yield ()).commit

  private def doUpdate(key: String, v: Option[PMetric]) =
    v match {
      case None    => ZSTM.succeed(())
      case Some(m) => items.put(key, m)
    }

  def list: ZIO[Any, Nothing, List[PMetric]] = items.values.commit

  private def createOrGet(
    key: String,
    zero: PMetric
  ): ZSTM[Any, Nothing, Option[PMetric]] = for {
    name <- ZSTM.succeed(key)
    pm   <- items.getOrElse(name, zero)
    r     = if (pm.details.getClass == zero.details.getClass) Some(pm) else None
  } yield r

}
