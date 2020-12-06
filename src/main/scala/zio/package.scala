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

import java.util.concurrent.ThreadLocalRandom

import zio.Supervisor.Propagation
import zio.clock.Clock
import zio.internal.RingBuffer
import zio.zmx.diagnostics.{ ZMXConfig, ZMXServer }
import zio.zmx.diagnostics.graph.{ Edge, Graph, Node }
import zio.zmx.metrics._
import zio.zmx.diagnostics.graph._

package object zmx extends MetricsDataModel with MetricsConfigDataModel {

  val ZMXSupervisor: Supervisor[FiberGraph] =
    new Supervisor[FiberGraph] {

      private val graphRef: UIO[Ref[Graph[Fiber.Runtime[Any, Any], String, String]]] =
        Ref.make(Graph.empty[Fiber.Runtime[Any, Any], String, String])

      def value: zio.UIO[FiberGraph] = graphRef.map(g => FiberGraph.apply(g))

      private[zio] def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A]): Propagation = {
        for {
          g <- graphRef
          _ <- g.updateAndGet((m: Graph[Fiber.Runtime[Any, Any], String, String]) =>
                 if (m.successors(fiber).isEmpty)
                   m.removeNode(fiber)
                 else
                   m
               )
        } yield ()
        Propagation.Continue
      }

      private[zio] def unsafeOnStart[R, E, A](
        environment: R,
        effect: ZIO[R, E, A],
        parent: Option[Fiber.Runtime[Any, Any]],
        fiber: Fiber.Runtime[E, A]
      ): Propagation = {
        for {
          g <- graphRef
          _ <- g.updateAndGet { (m: Graph[Fiber.Runtime[Any, Any], String, String]) =>
                 val n = m.addNode(Node(fiber, s"#${fiber.id.seqNumber}"))
                 parent match {
                   case Some(parent) =>
                     n.addEdge(Edge(parent, fiber, s"#${parent.id.seqNumber} -> #${fiber.id.seqNumber}"))
                   case None         => n
                 }
               }
        } yield ()

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
        ZMXServer
          .make(ZMXConfig(host, port, true))
          .as(new Service {})
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

    val enable: ZIO[CoreMetrics, Nothing, Unit] = ZIO.accessM[CoreMetrics](_.get.enable)

    val disable: ZIO[CoreMetrics, Nothing, Unit] = ZIO.accessM[CoreMetrics](_.get.disable)

    val isEnabled: ZIO[CoreMetrics, Nothing, Boolean] = ZIO.accessM[CoreMetrics](_.get.isEnabled)

    /**
     * The `CoreMetrics` service installs hooks into ZIO runtime system to track
     * important core metrics, such as number of fibers, fiber status, fiber
     * lifetimes, etc.
     */
    def live: ZLayer[Metrics, Nothing, CoreMetrics] = ???
  }

  type Metrics = Has[Metrics.Service]

  object Metrics {
    trait Service {

      def counter(name: String, value: Double): UIO[Boolean]

      def counter(name: String, value: Double, sampleRate: Double, tags: Label*): UIO[Boolean]

      def increment(name: String): UIO[Boolean]

      def increment(name: String, sampleRate: Double, tags: Label*): UIO[Boolean]

      def decrement(name: String): UIO[Boolean]

      def decrement(name: String, sampleRate: Double, tags: Label*): UIO[Boolean]

      def gauge(name: String, value: Double, tags: Label*): UIO[Boolean]

      def meter(name: String, value: Double, tags: Label*): UIO[Boolean]

      def timer(name: String, value: Double): UIO[Boolean]

      def timer(name: String, value: Double, sampleRate: Double, tags: Label*): UIO[Boolean]

      def set(name: String, value: String, tags: Label*): UIO[Boolean]

      def histogram(name: String, value: Double): UIO[Boolean]

      def histogram(name: String, value: Double, sampleRate: Double, tags: Label*): UIO[Boolean]

      def serviceCheck(name: String, status: ServiceCheckStatus): UIO[Boolean] =
        serviceCheck(name, status, None, None, None)

      def serviceCheck(
        name: String,
        status: ServiceCheckStatus,
        timestamp: Option[Long],
        hostname: Option[String],
        message: Option[String],
        tags: Label*
      ): UIO[Boolean]

      def event(name: String, text: String): UIO[Boolean] =
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
      ): UIO[Boolean]

      def listen(): ZIO[Any, Throwable, Fiber.Runtime[Throwable, Nothing]]

      def listen(
        f: Chunk[Metric[_]] => Task[Chunk[Long]]
      ): ZIO[Any, Throwable, Fiber.Runtime[Throwable, Nothing]]

    }

    private[zio] class Live(
      config: MetricsConfig,
      clock: Clock.Service,
      udpClient: UDPClient.Service,
      aggregator: Ref[Chunk[Metric[_]]]
    ) extends Service {

      private val ring: RingBuffer[Metric[_]] = RingBuffer[Metric[_]](config.maximumSize)

      override def counter(name: String, value: Double): UIO[Boolean] =
        send(Metric.Counter(name, value, 1.0, Chunk.empty))

      override def counter(name: String, value: Double, sampleRate: Double, tags: Label*): UIO[Boolean] =
        send(Metric.Counter(name, value, sampleRate, Chunk.fromIterable(tags)))

      override def increment(name: String): UIO[Boolean] =
        send(Metric.Counter(name, 1.0, 1.0, Chunk.empty))

      override def increment(name: String, sampleRate: Double, tags: Label*): UIO[Boolean] =
        send(Metric.Counter(name, 1.0, sampleRate, Chunk.fromIterable(tags)))

      override def decrement(name: String): UIO[Boolean] =
        send(Metric.Counter(name, -1.0, 1.0, Chunk.empty))

      override def decrement(name: String, sampleRate: Double, tags: Label*): UIO[Boolean] =
        send(Metric.Counter(name, -1.0, sampleRate, Chunk.fromIterable(tags)))

      override def gauge(name: String, value: Double, tags: Label*): UIO[Boolean] =
        send(Metric.Gauge(name, value, Chunk.fromIterable(tags)))

      override def meter(name: String, value: Double, tags: Label*): UIO[Boolean] =
        send(Metric.Gauge(name, value, Chunk.fromIterable(tags)))

      override def timer(name: String, value: Double): UIO[Boolean] =
        send(Metric.Timer(name, value, 1.0, Chunk.empty))

      override def timer(name: String, value: Double, sampleRate: Double, tags: Label*): UIO[Boolean] =
        send(Metric.Timer(name, value, sampleRate, Chunk.fromIterable(tags)))

      override def set(name: String, value: String, tags: Label*): UIO[Boolean] =
        send(Metric.Set(name, value, Chunk.fromIterable(tags)))

      override def histogram(name: String, value: Double): UIO[Boolean] =
        send(Metric.Histogram(name, value, 1.0, Chunk.empty))

      override def histogram(name: String, value: Double, sampleRate: Double, tags: Label*): UIO[Boolean] =
        send(Metric.Histogram(name, value, sampleRate, Chunk.fromIterable(tags)))

      override def serviceCheck(
        name: String,
        status: ServiceCheckStatus,
        timestamp: Option[Long],
        hostname: Option[String],
        message: Option[String],
        tags: Label*
      ): UIO[Boolean] =
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
      ): UIO[Boolean] =
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

      private def send(metric: Metric[_]): UIO[Boolean] = UIO(ring.offer(metric))

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

      private[zio] val udp: Chunk[Metric[_]] => Task[Chunk[Long]] =
        metrics => {
          val chunks: Chunk[Chunk[Byte]] = sample(metrics)
            .map(Encoder.encode)
            .map(s => s.getBytes())
            .map(Chunk.fromArray)
          IO.foreach(chunks)(udpClient.write)
        }

      private[zio] val poll: UIO[Chunk[Metric[_]]] =
        UIO(ring.poll(Metric.Zero)).flatMap {
          case Metric.Zero => aggregator.get
          case m @ _       =>
            aggregator.updateAndGet(_ :+ m)
        }

      private[zio] def drain: UIO[Unit] =
        UIO(ring.poll(Metric.Zero)).flatMap {
          case Metric.Zero => ZIO.unit
          case m @ _       => aggregator.updateAndGet(_ :+ m) *> drain
        }

      private val untilNCollected                                                                   =
        Schedule.fixed(config.pollRate) *>
          Schedule.recurUntil[Chunk[Metric[_]]](_.size == config.bufferSize)
      private[zio] val collect: (Chunk[Metric[_]] => Task[Chunk[Long]]) => Task[Chunk[Chunk[Long]]] =
        f => {
          for {
            _             <- poll
                               .repeat(untilNCollected)
                               .timeout(config.timeout)
                               .provide(Has(clock))
            _             <- drain
            metrics       <- aggregator.getAndUpdate(_ => Chunk.empty)
            groupedMetrics = Chunk(metrics.grouped(config.bufferSize).toSeq: _*)
            l             <- ZIO.foreach(groupedMetrics)(f)
          } yield l
        }

      val listen: ZIO[Any, Throwable, Fiber.Runtime[Throwable, Nothing]] = listen(udp)

      def listen(
        f: Chunk[Metric[_]] => Task[Chunk[Long]]
      ): ZIO[Any, Throwable, Fiber.Runtime[Throwable, Nothing]] =
        collect(f).forever.forkDaemon

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

    val listen: ZIO[Metrics, Throwable, Fiber.Runtime[Throwable, Nothing]] =
      ZIO.accessM[Metrics](_.get.listen())

    def listen(
      f: Chunk[Metric[_]] => IO[Exception, Chunk[Long]]
    ): ZIO[Metrics, Throwable, Fiber.Runtime[Throwable, Nothing]] =
      ZIO.accessM[Metrics](_.get.listen(f))

    /**
     * Constructs a live `Metrics` service based on the given configuration.
     */
    def live(config: MetricsConfig): RLayer[Clock, Metrics] =
      ZLayer.identity[Clock] ++ UDPClient.live(config) >>>
        ZLayer.fromServicesM[Clock.Service, UDPClient.Service, Any, Throwable, Metrics.Service] { (clock, udpClient) =>
          for {
            aggregator <- Ref.make[Chunk[Metric[_]]](Chunk.empty)
          } yield new Live(config, clock, udpClient, aggregator)
        }
  }

}
