package zio.zmx.prometheus

import zio._
import zio.stm._

private[zmx] object PrometheusRegistry {

  def make = TMap.empty[String, PMetric[_]].commit.map(m => new PrometheusRegistry(m))
}

private[zmx] final class PrometheusRegistry private (
  items: TMap[String, PMetric[_]]
) {

  def update[A <: PMetric.Details](
    name: String
  )(f: PMetric[A] => Option[PMetric[A]])(implicit tag: Tag[A]): ZIO[Any, Nothing, Unit] = (for {
    pm <- createOrGet[A](name)
    m   = pm.flatMap(f)
    _  <- ZSTM.foreach_(m)(m => insert(m))
  } yield ()).commit

  /**
   * Get the current list of captured metrics
   */
  def list: ZIO[Any, Nothing, List[PMetric[_]]] =
    items.values.commit

  /**
   * Use the given metrics registry name to look up a metric currently in the registry.
   * If the stored metric has the type as the given metric, return that. If no metric
   * has been found, store the given metric in the map and return it unmodified.
   * If the registry already contains a metric with the same registryKey, but another type
   * return None
   */
  private def createOrGet[A <: PMetric.Details](
    name: String
  )(implicit tag: Tag[A]): ZSTM[Any, Nothing, Option[PMetric[A]]] =
    for {
      r <- ZSTM.ifM(items.contains(name))(
             onTrue = lookup[A](name),
             onFalse = insert(PMetric.create[A](name))
           )
    } yield r

  private def lookup[A <: PMetric.Details](k: String)(implicit tag: Tag[A]): ZSTM[Any, Nothing, Option[PMetric[A]]] =
    for {
      item <- items.get(k)
      clazz = item.map(i => i.details.getClass).getOrElse(None.getClass())
      _     = if (clazz == tag.closestClass) item else None
    } yield None

  private def insert[A <: PMetric.Details](m: PMetric[A]): ZSTM[Any, Nothing, Option[PMetric[A]]] =
    items.put(m.registryKey, m).map(_ => Some(m))

}
