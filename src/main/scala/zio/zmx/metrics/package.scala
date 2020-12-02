package zio.zmx

import zio._

package object metrics extends MetricsDataModel {

  type ZMetrics = Has[ZMetrics.Service]

  // A generic metrics service
  object ZMetrics {
    trait Service {
      def counter(name: String): ZIO[Any, Nothing, Option[Metric.Counter]]
      def increment(m: Metric.Counter): ZIO[Any, Nothing, Option[Metric.Counter]]
    }

    def counter(name: String)        = ZIO.accessM[ZMetrics](_.get.counter(name))
    def increment(m: Metric.Counter) = ZIO.accessM[ZMetrics](_.get.increment(m))

    def count[R, E, A](name: String)(e: ZIO[R, E, A]): ZIO[R with ZMetrics, Any, A] = for {
      cnt <- counter(name)
      r   <- e
      _   <- ZIO.foreach(cnt)(cnt => increment(cnt))
    } yield r
  }

  lazy val prometheus: ZMetrics.Service          = new PrometheusInstrumentaion()
  lazy val statsd: ZMetrics.Service              = new StatsdInstrumentation()
  lazy val empty: ZLayer[Any, Nothing, ZMetrics] = ZLayer.succeed(new EmptyInstrumentation())
}
