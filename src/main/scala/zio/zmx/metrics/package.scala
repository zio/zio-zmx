package zio.zmx

import zio._

package object metrics {

  object ZMX {
    def count[R, E, A](name: String)(e: ZIO[R, E, A]): ZIO[R, E, A] = for {
      r <- e
      _ <- MetricsChannel.recordOption(MetricsDataModel.count(name))
    } yield r
  }

  implicit class MZio[R, E, A](z: ZIO[R, E, A]) {

    def counted(name: String): ZIO[R, E, A] = ZMX.count[R, E, A](name)(z)
  }
}
