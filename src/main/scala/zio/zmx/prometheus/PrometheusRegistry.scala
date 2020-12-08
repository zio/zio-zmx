package zio.zmx.prometheus

import zio._
import zio.stm._

private[zmx] object PrometheusRegistry {

  def make = TMap.empty[String, PMetric[PMetric.Details]].commit.map(i => new PrometheusRegistry(i))
}

private[zmx] final class PrometheusRegistry private (
  items: TMap[String, PMetric[PMetric.Details]]
) {

  def update[A <: PMetric.Details](
    zero: PMetric[A]
  )(f: PMetric[A] => Option[PMetric[A]]): ZIO[Any, Nothing, Unit] = (for {
    om <- createOrGet[A](zero)
    _  <- om match {
            case None    => ZSTM.none
            case Some(m) => doUpdate(f(m.asInstanceOf[PMetric[A]]))
          }
  } yield ()).commit

  private def doUpdate[A <: PMetric.Details](
    v: Option[PMetric[A]]
  ): ZSTM[Any, Nothing, Unit] =
    v match {
      case None    => ZSTM.succeed(())
      case Some(m) => items.put(m.registryKey, m)
    }

  def list: ZIO[Any, Nothing, List[PMetric[_]]] = items.values.commit

  private def createOrGet[A <: PMetric.Details](
    zero: PMetric[A]
  ): ZSTM[Any, Nothing, Option[PMetric[A]]] = for {
    name <- ZSTM.succeed(zero.registryKey)
    pm   <- items.getOrElse(name, zero)
    r     = pm.details match {
              case _: PMetric.Counter if zero.details.isInstanceOf[PMetric.Counter]     =>
                Some(pm.asInstanceOf[PMetric[PMetric.Counter]])
              case _: PMetric.Gauge if zero.details.isInstanceOf[PMetric.Gauge]         =>
                Some(pm.asInstanceOf[PMetric[PMetric.Gauge]])
              case _: PMetric.Histogram if zero.details.isInstanceOf[PMetric.Histogram] =>
                Some(pm.asInstanceOf[PMetric[PMetric.Histogram]])
              case _: PMetric.Summary if zero.details.isInstanceOf[PMetric.Summary]     =>
                Some(pm.asInstanceOf[PMetric[PMetric.Summary]])
              case _                                                                    => None
            }
  } yield r.map(_.asInstanceOf[PMetric[A]])

}
