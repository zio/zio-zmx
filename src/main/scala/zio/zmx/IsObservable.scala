package zio.zmx

import zio._

trait IsObservable[A] {
  def observe[R, E](zio: ZIO[R, E, A], key: MetricKey): ZIO[R, E, A]
}

object IsObservable {

  implicit case object StringIsObservable extends IsObservable[String] {
    def observe[R, E](zio: ZIO[R, E, String], key: MetricKey): ZIO[R, E, String] =
      zio.tap { a =>
        key match {
          case ok: MetricKey.Occurence => observeString(ok, a.asInstanceOf[String])
          case _                       => ZIO.unit
        }
      }
  }

  implicit case object DoubleIsObservable extends IsObservable[Double] {
    def observe[R, E](zio: ZIO[R, E, Double], key: MetricKey): ZIO[R, E, Double] =
      zio.tap { a =>
        key match {
          case gk: MetricKey.Gauge     => setGauge(gk, a.asInstanceOf[Double])
          case hk: MetricKey.Histogram => observeInHistogram(hk, a.asInstanceOf[Double])
          case sk: MetricKey.Summary   => observeInSummary(sk, a.asInstanceOf[Double])
          case _                       => ZIO.unit
        }
      }
  }
}
