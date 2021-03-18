package zio.zmx

import zio._

trait IsObservable[A] {
  def observe[R, E](zio: ZIO[R, E, A], name: String, tags: Label*): ZIO[R, E, A]
}

object IsObservable {

  implicit case object StringIsObservable extends IsObservable[String] {
    def observe[R, E](zio: ZIO[R, E, String], name: String, tags: Label*): ZIO[R, E, String] =
      zio.tap(a => observeString(name, a.asInstanceOf[String], tags: _*))
  }

  implicit case object DoubleIsObservable extends IsObservable[Double] {
    def observe[R, E](zio: ZIO[R, E, Double], name: String, tags: Label*): ZIO[R, E, Double] =
      zio.tap(a => observeDouble(name, a.asInstanceOf[Double], HistogramType.Histogram, tags: _*))
  }
}
