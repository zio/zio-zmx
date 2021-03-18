package zio.zmx.state

import zio._
import zio.zmx.Label

final case class MetricState(
  name: String,
  help: String,
  labels: Chunk[Label],
  details: MetricStateDetails
) {
  override def toString(): String = {
    val lbls = if (labels.isEmpty) "" else labels.map(l => s"${l._1}->${l._2}").mkString("{", ",", "}")
    s"MetricState($name$lbls, $details)"
  }
}
