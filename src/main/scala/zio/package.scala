/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio

import java.util.concurrent.{ ScheduledFuture, ScheduledThreadPoolExecutor, ThreadLocalRandom, TimeUnit }
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

import zio.Supervisor.Propagation
import zio.clock.Clock
import zio.internal.RingBuffer
import zio.zmx.diagnostics.{ ZMXConfig, ZMXServer }
import zio.zmx.diagnostics.fibers.FiberDumpProvider
import zio.zmx.diagnostics.parser.ZMXParser
import zio.zmx.metrics._

import scala.collection._
import scala.collection.immutable.SortedSet
import zio.zmx.diagnostics.graph.{ Edge, Graph, Node }

package object zmx extends MetricsDataModel with MetricsConfigDataModel {

  val ZMXSupervisor: Supervisor[SortedSet[Fiber.Runtime[Any, Any]]] =
    new Supervisor[SortedSet[Fiber.Runtime[Any, Any]]] {

      private[this] val graphRef: AtomicReference[Graph[Fiber.Runtime[Any, Any], String, String]] = new AtomicReference(
        Graph.empty[Fiber.Runtime[Any, Any], String, String]
      )

      def value: UIO[SortedSet[Fiber.Runtime[Any, Any]]] =
        UIO(SortedSet(graphRef.get.nodes.map(_.node).toSeq: _*))

      def unsafeOnStart[R, E, A](
        environment: R,
        effect: ZIO[R, E, A],
        parent: Option[Fiber.Runtime[Any, Any]],
        fiber: Fiber.Runtime[E, A]
      ): Propagation = {
        graphRef.updateAndGet(new UnaryOperator[Graph[Fiber.Runtime[Any, Any], String, String]] {
          override def apply(m: Graph[Fiber.Runtime[Any, Any], String, String]) = {
            val n = m.addNode(Node(fiber, s"#${fiber.id.seqNumber}"))
            parent match {
              case Some(parent) => n.addEdge(Edge(parent, fiber, s"#${parent.id.seqNumber} -> #${fiber.id.seqNumber}"))
              case None         => n
            }
          }
        })

        Propagation.Continue
      }

      def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A]): Propagation = {
        graphRef.updateAndGet(new UnaryOperator[Graph[Fiber.Runtime[Any, Any], String, String]] {
          override def apply(m: Graph[Fiber.Runtime[Any, Any], String, String]) =
            if (m.successors(fiber).size == 0)
              m.removeNode(fiber)
            else
              m
        })

        Propagation.Continue
      }
    }

  type Diagnostics = Has[Diagnostics.Service]

  object Diagnostics {
    trait Service {}

    /**
     * The Diagnostics service will listen on the specified port for commands to perform fiber
     * dumps, either across all fibers or across the specified fiber ids.
     */
    def live(host: String, port: Int): ZLayer[ZEnv, Throwable, Diagnostics] =
      ZLayer.fromManaged(
        ZManaged
          .make(
            ZMXServer
              .make(ZMXConfig(host, port, true))
              .provideCustomLayer(ZMXParser.respParser ++ FiberDumpProvider.live(ZMXSupervisor))
          )(_.shutdown.orDie)
          .map(_ => new Service {})
      )
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

      def counter(name: String, value: Double, sampleRate: Double, tags: Label*): F[Boolean]

      def increment(name: String): F[Boolean]

      def increment(name: String, sampleRate: Double, tags: Label*): F[Boolean]

      def decrement(name: String): F[Boolean]

      def decrement(name: String, sampleRate: Double, tags: Label*): F[Boolean]

      def gauge(name: String, value: Double, tags: Label*): F[Boolean]

      def meter(name: String, value: Double, tags: Label*): F[Boolean]

      def timer(name: String, value: Double): F[Boolean]

      def timer(name: String, value: Double, sampleRate: Double, tags: Label*): F[Boolean]

      def set(name: String, value: String, tags: Label*): F[Boolean]

      def histogram(name: String, value: Double): F[Boolean]

      def histogram(name: String, value: Double, sampleRate: Double, tags: Label*): F[Boolean]

      def serviceCheck(name: String, status: ServiceCheckStatus): F[Boolean] =
        serviceCheck(name, status, None, None, None)

      def serviceCheck(
        name: String,
        status: ServiceCheckStatus,
        timestamp: Option[Long],
        hostname: Option[String],
        message: Option[String],
        tags: Label*
      ): F[Boolean]

      def event(name: String, text: String): F[Boolean] =
        event(name, text, None, None, None, None, None, None)

      def event(
        name: String,
        text: String,
        timestamp: Option[Long],
        hostname: Option[String],
        aggregationKey: Option[String],
        priority: Option[EventPriority],
        sourceTypeName: Option[String],
        alertType: Option[EventAlertType],
        tags: Label*
      ): F[Boolean]

      def listen(): ZIO[Clock, Throwable, Fiber.Runtime[Throwable, Nothing]]

      def listen(
        f: Chunk[Metric[_]] => IO[Exception, Chunk[Long]]
      ): ZIO[Clock, Throwable, Fiber.Runtime[Throwable, Nothing]]
    }

    private[zio] type Id[+A] = A

    private[zio] trait UnsafeService extends AbstractService[Id] { self =>
      private[zio] def unsafeService: UnsafeService = self
    }

    private[zio] class RingUnsafeService(config: MetricsConfig) extends UnsafeService {

      override def counter(name: String, value: Double): Boolean =
        send(Metric.Counter(name, value, 1.0, Chunk.empty))

      override def counter(name: String, value: Double, sampleRate: Double, tags: Label*): Boolean =
        send(Metric.Counter(name, value, sampleRate, Chunk.fromIterable(tags)))

      override def increment(name: String): Boolean =
        send(Metric.Counter(name, 1.0, 1.0, Chunk.empty))

      override def increment(name: String, sampleRate: Double, tags: Label*): Boolean =
        send(Metric.Counter(name, 1.0, sampleRate, Chunk.fromIterable(tags)))

      override def decrement(name: String): Boolean =
        send(Metric.Counter(name, -1.0, 1.0, Chunk.empty))

      override def decrement(name: String, sampleRate: Double, tags: Label*): Boolean =
        send(Metric.Counter(name, -1.0, sampleRate, Chunk.fromIterable(tags)))

      override def gauge(name: String, value: Double, tags: Label*): Boolean =
        send(Metric.Gauge(name, value, Chunk.fromIterable(tags)))

      override def meter(name: String, value: Double, tags: Label*): Boolean =
        send(Metric.Gauge(name, value, Chunk.fromIterable(tags)))

      override def timer(name: String, value: Double): Boolean =
        send(Metric.Timer(name, value, 1.0, Chunk.empty))

      override def timer(name: String, value: Double, sampleRate: Double, tags: Label*): Boolean =
        send(Metric.Timer(name, value, sampleRate, Chunk.fromIterable(tags)))

      override def set(name: String, value: String, tags: Label*): Boolean =
        send(Metric.Set(name, value, Chunk.fromIterable(tags)))

      override def histogram(name: String, value: Double): Boolean =
        send(Metric.Histogram(name, value, 1.0, Chunk.empty))

      override def histogram(name: String, value: Double, sampleRate: Double, tags: Label*): Boolean =
        send(Metric.Histogram(name, value, sampleRate, Chunk.fromIterable(tags)))

      override def serviceCheck(
        name: String,
        status: ServiceCheckStatus,
        timestamp: Option[Long],
        hostname: Option[String],
        message: Option[String],
        tags: Label*
      ): Boolean =
        send(Metric.ServiceCheck(name, status, timestamp, hostname, message, Chunk.fromIterable(tags)))

      override def event(
        name: String,
        text: String,
        timestamp: Option[Long],
        hostname: Option[String],
        aggregationKey: Option[String],
        priority: Option[EventPriority],
        sourceTypeName: Option[String],
        alertType: Option[EventAlertType],
        tags: Label*
      ): Boolean =
        send(
          Metric.Event(
            name,
            text,
            timestamp,
            hostname,
            aggregationKey,
            priority,
            sourceTypeName,
            alertType,
            Chunk.fromIterable(tags)
          )
        )

      private val ring      = RingBuffer[Metric[_]](config.maximumSize)
      private val udpClient = (config.host, config.port) match {
        case (None, None)       => UDPClient.clientM
        case (Some(h), Some(p)) => UDPClient.clientM(h, p)
        case (Some(h), None)    => UDPClient.clientM(h, 8125)
        case (None, Some(p))    => UDPClient.clientM("localhost", p)
      }

      private def send(metric: Metric[_]): Boolean =
        if (!ring.offer(metric)) {
          println(s"Can not send $metric because queue already has ${ring.size()} items")
          false
        } else true

      //val ring = RingBuffer[Metric[_]](config.maximumSize)
      private val aggregator: AtomicReference[Chunk[Metric[_]]] =
        new AtomicReference[Chunk[Metric[_]]](Chunk.empty)

      private def shouldSample(rate: Double): Boolean =
        if (rate >= 1.0 || ThreadLocalRandom.current.nextDouble <= rate) true else false

      private def sample(metrics: Chunk[Metric[_]]): Chunk[Metric[_]] =
        metrics.filter(m =>
          m match {
            case Metric.Counter(_, _, sampleRate, _)   => shouldSample(sampleRate)
            case Metric.Histogram(_, _, sampleRate, _) => shouldSample(sampleRate)
            case Metric.Timer(_, _, sampleRate, _)     => shouldSample(sampleRate)
            case _                                     => true
          }
        )

      private[zio] val udp: Chunk[Metric[_]] => IO[Exception, Chunk[Long]] =
        metrics => {
          val arr: Chunk[Chunk[Byte]] = sample(metrics)
            .map(Encoder.encode)
            .map(s => s.getBytes())
            .map(Chunk.fromArray)
          for {
            chunks <- Task.succeed[Chunk[Chunk[Byte]]](arr)
            longs  <- IO.foreach(chunks) { chk =>
                        println(s"Chunk: $chk")
                        udpClient.use(_.write(chk))
                      }
          } yield { println(s"Sent: $longs"); longs }
        }

      private[zio] val poll: UIO[Chunk[Metric[_]]] =
        UIO(
          ring.poll(Metric.Zero) match {
            case Metric.Zero => aggregator.get()
            case m @ _       =>
              val r = aggregator.updateAndGet(_ :+ m)
              println(r)
              r
          }
        )

      private val untilNCollected                                                            = Schedule.recurUntil[Chunk[Metric[_]]](_.size == config.bufferSize)
      private[zio] val collect: (Chunk[Metric[_]] => Task[Chunk[Long]]) => Task[Chunk[Long]] =
        f => {
          println("Poll")
          for {
            r <- poll.repeat(untilNCollected).provideLayer(Clock.live)
            _  = println(s"Processing poll: ${r.size}")
            l <- f(aggregator.getAndUpdate(_ => Chunk.empty))
          } yield l
        }

      private val everyNSec                                                                         = Schedule.spaced(config.timeout)
      private[zio] val sendIfNotEmpty: (Chunk[Metric[_]] => Task[Chunk[Long]]) => Task[Chunk[Long]] =
        f =>
          Task(aggregator.get()).flatMap { l =>
            if (!l.isEmpty) {
              println(s"Processing timeout: ${l.size}")
              f(aggregator.getAndUpdate(_ => Chunk.empty))
            } else Task(Chunk.empty)
          }

      def listen(): ZIO[Clock, Throwable, Fiber.Runtime[Throwable, Nothing]] = listen(udp)
      def listen(
        f: Chunk[Metric[_]] => IO[Exception, Chunk[Long]]
      ): ZIO[Clock, Throwable, Fiber.Runtime[Throwable, Nothing]] = {
        println(s"Listen: ${ring.size()}")
        collect(f).forever.forkDaemon <& sendIfNotEmpty(f).repeat(everyNSec).forkDaemon
      }

      private[zio] val unsafeClient                              = UDPClientUnsafe(config.host.getOrElse("localhost"), config.port.getOrElse(8125))
      private[zio] val udpUnsafe: Chunk[Metric[_]] => Chunk[Int] = metrics =>
        for {
          flt <- sample(metrics).map(Encoder.encode)
        } yield unsafeClient.send(flt)

      def listenUnsafe(): (ScheduledFuture[_], ScheduledFuture[_]) = listenUnsafe(udpUnsafe)
      def listenUnsafe(
        f: Chunk[Metric[_]] => Chunk[Int]
      ): (ScheduledFuture[_], ScheduledFuture[_]) = {
        val fixedExecutor = new ScheduledThreadPoolExecutor(2)
        val collectTask   = new Runnable {
          def run() =
            while (true)
              ring.poll(Metric.Zero) match {
                case Metric.Zero => ()
                case m @ _       =>
                  val l = aggregator.updateAndGet(_ :+ m)
                  if (l.size == config.bufferSize) {
                    println("Collected")
                    f(aggregator.getAndUpdate(_ => Chunk.empty)).foreach(println)
                  }
              }
        }

        val timeoutTask = new Runnable {
          def run() =
            if (!aggregator.get().isEmpty) {
              println("Timeout!")
              f(aggregator.getAndUpdate(_ => Chunk.empty)).foreach(println)
            }
        }

        val rateHook    = fixedExecutor.scheduleAtFixedRate(timeoutTask, 5, config.timeout.toMillis, TimeUnit.MILLISECONDS)
        val collectHook = fixedExecutor.schedule(collectTask, 500, TimeUnit.MILLISECONDS)
        (rateHook, collectHook)
      }
    }

    trait Service extends AbstractService[UIO]
    object Service {

      private[zio] def fromUnsafeService(unsafe: UnsafeService): Service =
        new Service {

          override def unsafeService: UnsafeService = unsafe

          override def counter(name: String, value: Double): zio.UIO[Boolean] =
            UIO(unsafe.counter(name, value))

          override def counter(name: String, value: Double, sampleRate: Double, tags: Label*): zio.UIO[Boolean] =
            UIO(unsafe.counter(name, value, sampleRate, tags: _*))

          override def increment(name: String): zio.UIO[Boolean] =
            UIO(unsafe.increment(name))

          override def increment(name: String, sampleRate: Double, tags: Label*): zio.UIO[Boolean] =
            UIO(unsafe.increment(name, sampleRate, tags: _*))

          override def decrement(name: String): zio.UIO[Boolean] =
            UIO(unsafe.decrement(name))

          override def decrement(name: String, sampleRate: Double, tags: Label*): zio.UIO[Boolean] =
            UIO(unsafe.decrement(name, sampleRate, tags: _*))

          override def gauge(name: String, value: Double, tags: Label*): zio.UIO[Boolean] =
            UIO(unsafe.gauge(name, value, tags: _*))

          override def meter(name: String, value: Double, tags: Label*): zio.UIO[Boolean] =
            UIO(unsafe.meter(name, value, tags: _*))

          override def timer(name: String, value: Double): zio.UIO[Boolean] =
            UIO(unsafe.timer(name, value))

          override def timer(name: String, value: Double, sampleRate: Double, tags: Label*): zio.UIO[Boolean] =
            UIO(unsafe.timer(name, value, sampleRate, tags: _*))

          override def set(name: String, value: String, tags: Label*): zio.UIO[Boolean] =
            UIO(unsafe.set(name, value, tags: _*))

          override def histogram(name: String, value: Double): zio.UIO[Boolean] =
            UIO(unsafe.histogram(name, value))

          override def histogram(name: String, value: Double, sampleRate: Double, tags: Label*): zio.UIO[Boolean] =
            UIO(unsafe.histogram(name, value, sampleRate, tags: _*))

          override def serviceCheck(
            name: String,
            status: ServiceCheckStatus,
            timestamp: Option[Long],
            hostname: Option[String],
            message: Option[String],
            tags: Label*
          ): UIO[Boolean] =
            UIO(unsafe.serviceCheck(name, status, timestamp, hostname, message, tags: _*))

          override def event(
            name: String,
            text: String,
            timestamp: Option[Long],
            hostname: Option[String],
            aggregationKey: Option[String],
            priority: Option[EventPriority],
            sourceTypeName: Option[String],
            alertType: Option[EventAlertType],
            tags: Label*
          ): UIO[Boolean] =
            UIO(
              unsafe
                .event(name, text, timestamp, hostname, aggregationKey, priority, sourceTypeName, alertType, tags: _*)
            )

          override def listen(): ZIO[Clock, Throwable, Fiber.Runtime[Throwable, Nothing]] = unsafe.listen()

          override def listen(
            f: Chunk[Metric[_]] => IO[Exception, Chunk[Long]]
          ): ZIO[Clock, Throwable, Fiber.Runtime[Throwable, Nothing]] = unsafe.listen(f)
        }
    }

    /**
     * Sets the counter of the specified name for the specified value.
     */
    def counter(name: String, value: Double): ZIO[Metrics, Nothing, Boolean] =
      ZIO.accessM[Metrics](_.get.counter(name, value))

    def counter(name: String, value: Double, sampleRate: Double, tags: Label*): ZIO[Metrics, Nothing, Boolean] =
      ZIO.accessM[Metrics](_.get.counter(name, value, sampleRate, tags: _*))

    def increment(name: String): ZIO[Metrics, Nothing, Boolean] =
      ZIO.accessM[Metrics](_.get.increment(name))

    def increment(name: String, sampleRate: Double, tags: Label*): ZIO[Metrics, Nothing, Boolean] =
      ZIO.accessM[Metrics](_.get.increment(name, sampleRate, tags: _*))

    def decrement(name: String): ZIO[Metrics, Nothing, Boolean] =
      ZIO.accessM[Metrics](_.get.decrement(name))

    def decrement(name: String, sampleRate: Double, tags: Label*): ZIO[Metrics, Nothing, Boolean] =
      ZIO.accessM[Metrics](_.get.decrement(name, sampleRate, tags: _*))

    def gauge(name: String, value: Double, tags: Label*): ZIO[Metrics, Nothing, Boolean] =
      ZIO.accessM[Metrics](_.get.gauge(name, value, tags: _*))

    def meter(name: String, value: Double, tags: Label*): ZIO[Metrics, Nothing, Boolean] =
      ZIO.accessM[Metrics](_.get.meter(name, value, tags: _*))

    def timer(name: String, value: Double): ZIO[Metrics, Nothing, Boolean] =
      ZIO.accessM[Metrics](_.get.timer(name, value))

    def set(name: String, value: String, tags: Label*): ZIO[Metrics, Nothing, Boolean] =
      ZIO.accessM[Metrics](_.get.set(name, value, tags: _*))

    def histogram(name: String, value: Double): ZIO[Metrics, Nothing, Boolean] =
      ZIO.accessM[Metrics](_.get.histogram(name, value))

    def histogram(name: String, value: Double, sampleRate: Double, tags: Label*): ZIO[Metrics, Nothing, Boolean] =
      ZIO.accessM[Metrics](_.get.histogram(name, value, sampleRate, tags: _*))

    def serviceCheck(name: String, status: ServiceCheckStatus): ZIO[Metrics, Nothing, Boolean] =
      ZIO.accessM[Metrics](_.get.serviceCheck(name, status))

    def serviceCheck(
      name: String,
      status: ServiceCheckStatus,
      timestamp: Option[Long],
      hostname: Option[String],
      message: Option[String],
      tags: Label*
    ): ZIO[Metrics, Nothing, Boolean] =
      ZIO.accessM[Metrics](_.get.serviceCheck(name, status, timestamp, hostname, message, tags: _*))

    def event(name: String, text: String): ZIO[Metrics, Nothing, Boolean] =
      ZIO.accessM[Metrics](_.get.event(name, text))

    def event(
      name: String,
      text: String,
      timestamp: Option[Long],
      hostname: Option[String],
      aggregationKey: Option[String],
      priority: Option[EventPriority],
      sourceTypeName: Option[String],
      alertType: Option[EventAlertType],
      tags: Label*
    ): ZIO[Metrics, Nothing, Boolean] =
      ZIO.accessM[Metrics](
        _.get.event(name, text, timestamp, hostname, aggregationKey, priority, sourceTypeName, alertType, tags: _*)
      )

    def listen(): ZIO[Clock with Metrics, Throwable, Fiber.Runtime[Throwable, Nothing]] =
      ZIO.accessM[Metrics](_.get.listen().provideSomeLayer(Clock.live))

    def listen(
      f: Chunk[Metric[_]] => IO[Exception, Chunk[Long]]
    ): ZIO[Clock with Metrics, Throwable, Fiber.Runtime[Throwable, Nothing]] =
      ZIO.accessM[Metrics](_.get.listen(f).provideSomeLayer(Clock.live))

    /**
     * Constructs a live `Metrics` service based on the given configuration.
     */
    def live(config: MetricsConfig): Layer[Nothing, Metrics] =
      ZLayer.succeed(Service.fromUnsafeService(new RingUnsafeService(config)))
  }

}
