package zio.zmx.client.frontend.utils

import zio._
import com.raquo.airstream.split.Splittable
import zio.metrics.MetricKey
import zio.metrics.MetricKey.Counter
import zio.metrics.MetricKey.Gauge
import zio.metrics.MetricKey.Histogram
import zio.metrics.MetricKey.SetCount
import zio.metrics.MetricKey.Summary

object Implicits {
  implicit val chunkSplittable: Splittable[Chunk] = new Splittable[Chunk] {
    override def map[A, B](inputs: Chunk[A], project: A => B): Chunk[B] =
      inputs.map(project)
  }

  implicit val iterableSplittable: Splittable[Iterable] = new Splittable[Iterable] {
    def map[A, B](inputs: Iterable[A], project: A => B): Iterable[B] = inputs.map(project)
  }

  implicit class MetricKeySyntax(self: MetricKey) {

    def longName: String = name + (if (labels.isEmpty) "" else s":$labelsAsString")

    def name: String = self match {
      case Counter(name, _)             => name
      case Gauge(name, _)               => name
      case Histogram(name, _, _)        => name
      case Summary(name, _, _, _, _, _) => name
      case SetCount(name, _, _)         => name
    }

    def labelsAsString = labels.map(l => s"${l.key}=${l.value}").mkString(",")

    def labels: Chunk[MetricLabel] = self match {
      case Counter(_, tags)             => tags
      case Gauge(_, tags)               => tags
      case Histogram(_, _, tags)        => tags
      case Summary(_, _, _, _, _, tags) => tags
      case SetCount(_, _, tags)         => tags
    }
  }
}
