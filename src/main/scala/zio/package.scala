package zio

import zio.clock.Clock
import zio.console.Console
import zmx.server.{ ZMXConfig, ZMXServer }

package object zmx extends MetricsDataModel with MetricsConfigDataModel {
  type Diagnostics = Has[Diagnostics.Service]

  object Diagnostics {
    trait Service {
      def start: Managed[Exception, ZMXServer] //TODO refine exception type
    }

    /**
     * The Diagnostics service will listen on the specified port for commands to perform fiber
     * dumps, either across all fibers or across the specified fiber ids.
     */
    def live(host: String, port: Int): ZLayer[Clock with Console, Throwable, Diagnostics] = 
      ZLayer.fromFunctionMany[Clock with Console, Diagnostics] { layer =>
        Has(new Service {
          val start = ZManaged.make(ZMXServer(ZMXConfig(host, port, true)))(_.shutdown.orDie).provide(layer)
        })
      }
    
    
    def start: ZManaged[Diagnostics with Console with Clock, Exception, ZMXServer] =
      ZManaged.accessManaged[Diagnostics](_.get.start)
  }

  // TODO Does this needs to be part of ZIO-Core?
  type CoreMetrics = Has[CoreMetrics.Service]

  object CoreMetrics {
    trait Service {
      def enable: UIO[Unit]

      def disable: UIO[Unit]

      def isEnabled: UIO[Boolean]
    }

    def enable: ZIO[CoreMetrics, Nothing, Unit] = ZIO.accessM[CoreMetrics](_.get.enable)

    def disable: ZIO[CoreMetrics, Nothing, Unit] = ZIO.accessM[CoreMetrics](_.get.disable)

    def isEnabled: ZIO[CoreMetrics, Nothing, Boolean] = ZIO.accessM[CoreMetrics](_.get.isEnabled)

    /**
     * The `CoreMetrics` service installs hooks into ZIO runtime system to track
     * important core metrics, such as number of fibers, fiber status, fiber
     * lifetimes, etc.
     */
    def live: ZLayer[Metrics, Nothing, CoreMetrics] = ???
  }

  type Metrics = Has[Metrics.Service]

  object Metrics {
    trait AbstractService[+F[+_]] {
      private[zio] def unsafeService: UnsafeService

      def counter(name: String, value: Double): F[Unit]

      def counter(name: String, value: Double, sampleRate: Double, tags: Tag*): F[Unit]

      def increment(name: String): F[Unit]

      def increment(name: String, sampleRate: Double, tags: Tag*): F[Unit]

      def decrement(name: String): F[Unit]

      def decrement(name: String, sampleRate: Double, tags: Tag*): F[Unit]

      def gauge(name: String, value: Double, tags: Tag*): F[Unit]

      def meter(name: String, value: Double, tags: Tag*): F[Unit]

      def timer(name: String, value: Double): F[Unit]

      def timer(name: String, value: Double, sampleRate: Double, tags: Tag*): F[Unit]

      def set(name: String, value: String, tags: Tag*): F[Unit]

      def histogram(name: String, value: Double): F[Unit]

      def histogram(name: String, value: Double, sampleRate: Double, tags: Tag*): F[Unit]

      def serviceCheck(name: String, status: ServiceCheckStatus): F[Unit] =
        serviceCheck(name, status, None, None, None, Seq.empty[Tag])

      def serviceCheck(
        name: String,
        status: ServiceCheckStatus,
        timestamp: Option[Long],
        hostname: Option[String],
        message: Option[String],
        tags: Seq[Tag]
      ): F[Unit]

      def event(name: String, text: String): F[Unit] =
        event(name, text, None, None, None, None, None, None, Seq.empty[Tag])

      def event(
        name: String,
        text: String,
        timestamp: Option[Long],
        hostname: Option[String],
        aggregationKey: Option[String],
        priority: Option[EventPriority],
        sourceTypeName: Option[String],
        alertType: Option[EventAlertType],
        tags: Seq[Tag]
      ): F[Unit]
    }

    private[zio] type Id[+A] = A

    private[zio] trait UnsafeService extends AbstractService[Id] { self =>
      private[zio] def unsafeService: UnsafeService = self
    }

    trait Service extends AbstractService[UIO]
    object Service {
      private[zio] def fromUnsafeService(unsafe: UnsafeService): Service =
        ???
    }

    /**
     * Sets the counter of the specified name for the specified value.
     */
    def counter(name: String, value: Double): ZIO[Metrics, Nothing, Unit] =
      ZIO.accessM[Metrics](_.get.counter(name, value))

    /**
     * Constructs a live `Metrics` service based on the given configuration.
     */
    def live(config: MetricsConfig): ZLayer[Any, Nothing, Metrics] = ???
  }

}
