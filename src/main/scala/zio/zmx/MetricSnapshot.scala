package zio.zmx

sealed trait MetricSnapshot

object MetricSnapshot {
  final case class Json(value: String)       extends MetricSnapshot
  final case class Prometheus(value: String) extends MetricSnapshot
}
