package zio.zmx.internal

import zio.zmx.Label

trait MetricListener {
  def incrementCounter(name: String, value: Double, tags: Label*): Unit
}
