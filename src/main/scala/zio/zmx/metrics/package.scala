package zio.zmx

import zio._
import zio.zmx.statsd.StatsdInstrumentation
import zio.zmx.prometheus.PrometheusInstrumentaion

package object metrics {

  type ZMetrics = Has[ZMetrics.Service]

  // A generic metrics service
  object ZMetrics {
    trait Service {
      def report: ZIO[ZEnv, Nothing, String] = ZIO.succeed("")
      def increment(name: String): ZIO[Any, Nothing, Unit]
    }

    def increment(name: String) = ZIO.accessM[ZMetrics](_.get.increment(name))

    def count[R, E, A](name: String)(e: ZIO[R, E, A]): ZIO[R with ZMetrics, E, A] = for {
      r <- e
      _ <- increment(name)
    } yield r
  }

  lazy val prometheus: ZLayer[Any, Nothing, ZMetrics] =
    ZLayer.fromEffect(PrometheusInstrumentaion.make)

  def statsd(cfg: MetricsConfigDataModel.MetricsConfig): ZLayer[Any, Nothing, ZMetrics] =
    ZLayer.succeed(new StatsdInstrumentation(cfg))

  lazy val empty: ZLayer[Any, Nothing, ZMetrics] = ZLayer.succeed(new EmptyInstrumentation())

  implicit class MZio[R, E, A](z: ZIO[R, E, A]) {

    import ZMetrics._

    def counted(name: String): ZIO[R with ZMetrics, E, A] = count[R, E, A](name)(z)
  }
}
