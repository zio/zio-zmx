package zio.zmx.client.frontend.utils

import java.time.Instant

import scala.scalajs.js

import com.raquo.airstream.split.Splittable

import zio._
import zio.metrics.MetricKey
import zio.metrics.MetricKey.Counter
import zio.metrics.MetricKey.Frequency
import zio.metrics.MetricKey.Gauge
import zio.metrics.MetricKey.Histogram
import zio.metrics.MetricKey.Summary
import zio.metrics.MetricLabel

object Implicits {
  implicit val chunkSplittable: Splittable[Chunk] = new Splittable[Chunk] {
    override def map[A, B](inputs: Chunk[A], project: A => B): Chunk[B] =
      inputs.map(project)
  }

  implicit val iterableSplittable: Splittable[Iterable] = new Splittable[Iterable] {
    def map[A, B](inputs: Iterable[A], project: A => B): Iterable[B] = inputs.map(project)
  }

  implicit class MetricKeySyntax(self: MetricKey[_]) {

    def longName: String = self.name + (if (self.tags.isEmpty) "" else s":$labelsAsString")

    def labelsAsString = self.tags.map { case MetricLabel(k, v) => s"$k=$v" }.mkString(",")
  }

  implicit class InstantOps(self: Instant) {
    def toJSDate: js.Date = new js.Date(self.toEpochMilli().doubleValue())
  }
}
