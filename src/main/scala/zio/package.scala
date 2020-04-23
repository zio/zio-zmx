package zio

package object zmx extends MetricsDataModel with MetricsConfigDataModel {

  import java.util.concurrent.atomic.AtomicReference

  import java.util.concurrent.{ ScheduledThreadPoolExecutor, TimeUnit }

  import zio.duration.Duration

  import zio.clock.Clock

  import java.util.concurrent.ThreadLocalRandom

  import zio.zmx.metrics._

  import zio.internal.impls.RingBuffer

  import zio.nio.channels.DatagramChannel

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

      def counter(name: String, value: Double): F[Boolean]

      def counter(name: String, value: Double, sampleRate: Double, tags: Tag*): F[Boolean]

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

      def send(metric: Metric[AnyVal]): Boolean
    }
    object UnsafeService {
      private val ring = RingBuffer[Metric[AnyVal]](100)
      private val udpClient: (Option[String], Option[Int]) => ZManaged[Any, Exception, DatagramChannel] =
        (host, port) =>
          (host, port) match {
            case (None, None)       => UDPClient.clientM
            case (Some(h), Some(p)) => UDPClient.clientM(h, p)
            case (Some(h), None)    => UDPClient.clientM(h, 8125)
            case (None, Some(p))    => UDPClient.clientM("localhost", p)
          }

      private def shouldSample(rate: Double): Boolean =
        if (rate >= 1.0 || ThreadLocalRandom.current.nextDouble <= rate) true else false

      private def sample(metrics: List[Metric[AnyVal]]): List[Metric[AnyVal]] =
        metrics.filter(m =>
          m match {
            case Metric.Counter(_, _, sampleRate, _)   => shouldSample(sampleRate)
            case Metric.Histogram(_, _, sampleRate, _) => shouldSample(sampleRate)
            case Metric.Timer(_, _, sampleRate, _)     => shouldSample(sampleRate)
            case _                                     => true
          }
        )

      private[zio] val udp: List[Metric[AnyVal]] => IO[Exception, List[Long]] = metrics => {
        val arr: List[Chunk[Byte]] = sample(metrics)
          .map(Encoder.encode)
          .map(s => s.getBytes())
          .map(Chunk.fromArray)
        for {
          chunks <- Task.succeed[List[Chunk[Byte]]](arr)
          longs <- IO.foreach(chunks) { chk =>
                    println(s"Chunk: $chk")
                    udpClient(None, None).use(_.write(chk))
                  }
        } yield { println(s"Sent: $longs"); longs }
      }

      def send(metric: Metric[AnyVal]): Boolean =
        if (!ring.offer(metric)) {
          println(s"Can not send $metric because queue already has ${ring.size()} items")
          false
        } else true

      private val aggregator: AtomicReference[List[Metric[AnyVal]]] =
        new AtomicReference[List[Metric[AnyVal]]](List.empty[Metric[AnyVal]])

      private[zio] val poll: UIO[List[Metric[AnyVal]]] =
        UIO(
          ring.poll(Metric.Zero) match {
            case Metric.Zero => aggregator.get()
            case m @ _ => {
              val r = aggregator.updateAndGet(_ :+ m)
              println(r)
              r
            }
          }
        )

      private val untilNCollected = Schedule.doUntil[List[Metric[AnyVal]]](_.size == 5)
      private[zio] val collect: (List[Metric[AnyVal]] => Task[List[Long]]) => Task[List[Long]] =
        f => {
          println("Poll")
          for {
            r <- poll.repeat(untilNCollected)
            _ = println(s"Processing poll: ${r.size}")
            l <- f(aggregator.getAndUpdate(_ => List.empty[Metric[AnyVal]]))
          } yield l
        }

      private val everyNSec = Schedule.spaced(Duration(5, TimeUnit.SECONDS))
      private[zio] val sendIfNotEmpty: (List[Metric[AnyVal]] => Task[List[Long]]) => Task[List[Long]] =
        f =>
          Task(aggregator.get()).flatMap { l =>
            if (!l.isEmpty) {
              println(s"Processing timeout: ${l.size}")
              f(aggregator.getAndUpdate(_ => List.empty[Metric[AnyVal]]))
            } else Task(List.empty[Long])
          }

      def listen(): ZIO[Clock, Throwable, Fiber.Runtime[Throwable, Nothing]] = listen(udp)
      def listen(
        f: List[Metric[AnyVal]] => Task[List[Long]]
      ) = {
        println(s"Listen: ${ring.size()}")
        collect(f).forever.forkDaemon <& sendIfNotEmpty(f).repeat(everyNSec).forkDaemon
      }

      private val client = UDPClientUnsafe("localhost", 8125)
      private val udpUnsafe: List[Metric[AnyVal]] => List[Int] = metrics =>
        for {
          flt <- sample(metrics).map(Encoder.encode).toList
        } yield client.send(flt)

      def listenUnsafe(): Unit = listenUnsafe(udpUnsafe)
      def listenUnsafe(
        f: List[Metric[AnyVal]] => List[Int]
      ): Unit = {
        val fixedExecutor = new ScheduledThreadPoolExecutor(2)
        val collectTask = new Runnable {
          def run() =
            while (true) {
              ring.poll(Metric.Zero) match {
                case Metric.Zero => ()
                case m @ _ => {
                  val l = aggregator.updateAndGet(_ :+ m)
                  if (l.size == 5) {
                    f(aggregator.getAndUpdate(_ => List.empty[Metric[AnyVal]]))
                  }
                }
              }
            }
        }

        val timeoutTask = new Runnable {
          def run() = if (!aggregator.get().isEmpty) {
            println("Timeout!")
            f(aggregator.getAndUpdate(_ => List.empty[Metric[AnyVal]])).foreach(println)
          }
        }

        fixedExecutor.scheduleAtFixedRate(timeoutTask, 5, 5, TimeUnit.SECONDS)
        fixedExecutor.schedule(collectTask, 500, TimeUnit.MILLISECONDS)
        ()
      }
    }

    trait Service extends AbstractService[UIO]
    object Service {

      private[zio] def fromUnsafeService(unsafe: UnsafeService): Service = new Service {

        override def unsafeService: UnsafeService = unsafe

        override def counter(name: String, value: Double): zio.UIO[Boolean] =
          UIO(unsafe.send(Metric.Counter(name, value, 1.0, Chunk.empty)))

        override def counter(name: String, value: Double, sampleRate: Double, tags: Tag*): zio.UIO[Boolean] =
          UIO(unsafe.send(Metric.Counter(name, value, sampleRate, Chunk.fromIterable(tags))))

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
    def counter(name: String, value: Double): ZIO[Metrics, Nothing, Boolean] =
      ZIO.accessM[Metrics](_.get.counter(name, value))

    /**
     * Constructs a live `Metrics` service based on the given configuration.
     */
    def live(config: MetricsConfig): ZLayer[Any, Nothing, Metrics] = ???
  }

}
