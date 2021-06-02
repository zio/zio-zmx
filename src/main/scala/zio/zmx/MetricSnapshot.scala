package zio.zmx

sealed trait MetricSnapshot[+T] {
  def value: T
}

object MetricSnapshot {
  final case class Json(override val value: String)       extends MetricSnapshot[String]
  final case class Prometheus(override val value: String) extends MetricSnapshot[String]
}
