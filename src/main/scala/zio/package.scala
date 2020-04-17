package zio

package object zmx extends MetricsDataModel with MetricsConfigDataModel {

  import java.util.concurrent.ThreadLocalRandom

  import zio.zmx.metrics._

  import zio.internal.impls.RingBuffer


  type Diagnostics = Has[Diagnostics.Service]

  object Diagnostics {
    trait Service {}

    /**
     * The Diagnostics service will listen on the specified port for commands to perform fiber
     * dumps, either across all fibers or across the specified fiber ids.
     */
    def live(port: Int, host: Option[String]): ZLayer[Any, Nothing, Diagnostics] = ???
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

      val ring = RingBuffer[Metric[_]](10)
      //private val duration: Finite = Finite(timeout)
     /* private val udpClient: (Option[String], Option[Int]) => ZManaged[Any, Exception, DatagramChannel] =
        (host, port) =>
          (host, port) match {
            case (None, None)       => UDPClient.clientM
            case (Some(h), Some(p)) => UDPClient.clientM(h, p)
            case (Some(h), None)    => UDPClient.clientM(h, 8125)
            case (None, Some(p))    => UDPClient.clientM("localhost", p)
          }*/

      private def shouldSample(rate: Double): Boolean =
        if (rate >= 1.0 || ThreadLocalRandom.current.nextDouble <= rate) true else false

      private def sample(metrics: List[Metric[_]]): List[Metric[_]] = metrics.filter(m =>
            m match {
              case Metric.Counter(_, _, sampleRate, _)   => shouldSample(sampleRate)
              case Metric.Histogram(_, _, sampleRate, _) => shouldSample(sampleRate)
              case Metric.Timer(_, _, sampleRate, _)     => shouldSample(sampleRate)
              case _                                     => true
            }
          )

      private val client = UDPClientUnsafe("localhost", 8125)

      private def udp(metrics: List[Metric[_]]): List[Int] =
        for {
          flt  <- sample(metrics).map(Encoder.encode)
        } yield client.send(flt)

      def listen: Fiber[Throwable, Unit] =
        listen[List](udp)

      def listen[F[_]](
        f: List[Metric[_]] => F[_]
      ) = ???
    }

    trait Service extends AbstractService[UIO]
    object Service {
      private[zio] def fromUnsafeService(unsafe: UnsafeService): Service = new Service {

        override def unsafeService: UnsafeService = unsafe

        override def counter(name: String, value: Double): zio.UIO[Unit] = ???

        override def counter(name: String, value: Double, sampleRate: Double, tags: Tag*): zio.UIO[Unit] = ???

        override def increment(name: String): zio.UIO[Unit] = ???

        override def increment(name: String, sampleRate: Double, tags: Tag*): zio.UIO[Unit] = ???

        override def decrement(name: String): zio.UIO[Unit] = ???

        override def decrement(name: String, sampleRate: Double, tags: Tag*): zio.UIO[Unit] = ???

        override def gauge(name: String, value: Double, tags: Tag*): zio.UIO[Unit] = ???

        override def meter(name: String, value: Double, tags: Tag*): zio.UIO[Unit] = ???

        override def timer(name: String, value: Double): zio.UIO[Unit] = ???

        override def timer(name: String, value: Double, sampleRate: Double, tags: Tag*): zio.UIO[Unit] = ???

        override def set(name: String, value: String, tags: Tag*): zio.UIO[Unit] = ???

        override def histogram(name: String, value: Double): zio.UIO[Unit] = ???

        override def histogram(name: String, value: Double, sampleRate: Double, tags: Tag*): zio.UIO[Unit] = ???

        override def serviceCheck(
          name: String,
          status: ServiceCheckStatus,
          timestamp: Option[Long],
          hostname: Option[String],
          message: Option[String],
          tags: Seq[Tag]
        ): zio.UIO[Unit] = ???

        override def event(
          name: String,
          text: String,
          timestamp: Option[Long],
          hostname: Option[String],
          aggregationKey: Option[String],
          priority: Option[EventPriority],
          sourceTypeName: Option[String],
          alertType: Option[EventAlertType],
          tags: Seq[Tag]
        ): zio.UIO[Unit] = ???

      }
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
