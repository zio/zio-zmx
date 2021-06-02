package zio.zmx

sealed trait MetricSnapshot {
  def value: String
}

object MetricSnapshot {
  final case class Json(override val value: String)       extends MetricSnapshot
  final case class Prometheus(override val value: String) extends MetricSnapshot
}
