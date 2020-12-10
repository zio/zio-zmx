package zio.zmx.statsd

import zio._

import zio.zmx.statsd.StatsdDataModel._
import zio.zmx.MetricsConfig
import zio.zmx.metrics.MetricsDataModel._
import zio.zmx.metrics.Instrumentation

final class StatsdInstrumentation(config: MetricsConfig) extends Instrumentation {

  private val statsdClient = StatsdClient.live(config).orDie

  def handleMetric(m: MetricEvent): ZIO[Any, Nothing, Unit] = m.details match {
    case c: MetricEventDetails.Count => send(Metric.Counter(m.name, c.v, 1d, Chunk.empty))
  }

  private def send(m: Metric[_]): ZIO[Any, Nothing, Unit] = (for {
    clt <- ZIO.service[StatsdClient.StatsdClientSvc]
    buf  = Chunk.fromArray(StatsdEncoder.encode(m).getBytes())
    _   <- clt.write(buf)
  } yield ()).provideLayer(statsdClient).catchAll(_ => ZIO.succeed(()))

}

// private[zmx] class Live(
//   config: MetricsConfig,
//   clock: Clock.Service,
//   statsdClient: StatsdClient.Service,
//   aggregator: Ref[Chunk[Metric[_]]]
// ) extends Metrics.Service {
//
//   private val ring: RingBuffer[Metric[_]] = RingBuffer[Metric[_]](config.maximumSize)
//
//   override def counter(name: String, value: Double): UIO[Boolean] =
//     send(Metric.Counter(name, value, 1.0, Chunk.empty))
//
//   override def counter(name: String, value: Double, sampleRate: Double, tags: Label*): UIO[Boolean] =
//     send(Metric.Counter(name, value, sampleRate, Chunk.fromIterable(tags)))
//
//   override def increment(name: String): UIO[Boolean] =
//     send(Metric.Counter(name, 1.0, 1.0, Chunk.empty))
//
//   override def increment(name: String, sampleRate: Double, tags: Label*): UIO[Boolean] =
//     send(Metric.Counter(name, 1.0, sampleRate, Chunk.fromIterable(tags)))
//
//   override def decrement(name: String): UIO[Boolean] =
//     send(Metric.Counter(name, -1.0, 1.0, Chunk.empty))
//
//   override def decrement(name: String, sampleRate: Double, tags: Label*): UIO[Boolean] =
//     send(Metric.Counter(name, -1.0, sampleRate, Chunk.fromIterable(tags)))
//
//   override def gauge(name: String, value: Double, tags: Label*): UIO[Boolean] =
//     send(Metric.Gauge(name, value, Chunk.fromIterable(tags)))
//
//   override def meter(name: String, value: Double, tags: Label*): UIO[Boolean] =
//     send(Metric.Gauge(name, value, Chunk.fromIterable(tags)))
//
//   override def timer(name: String, value: Double): UIO[Boolean] =
//     send(Metric.Timer(name, value, 1.0, Chunk.empty))
//
//   override def timer(name: String, value: Double, sampleRate: Double, tags: Label*): UIO[Boolean] =
//     send(Metric.Timer(name, value, sampleRate, Chunk.fromIterable(tags)))
//
//   override def set(name: String, value: String, tags: Label*): UIO[Boolean] =
//     send(Metric.Set(name, value, Chunk.fromIterable(tags)))
//
//   override def histogram(name: String, value: Double): UIO[Boolean] =
//     send(Metric.Histogram(name, value, 1.0, Chunk.empty))
//
//   override def histogram(name: String, value: Double, sampleRate: Double, tags: Label*): UIO[Boolean] =
//     send(Metric.Histogram(name, value, sampleRate, Chunk.fromIterable(tags)))
//
//   override def serviceCheck(
//     name: String,
//     status: ServiceCheckStatus,
//     timestamp: Option[Long],
//     hostname: Option[String],
//     message: Option[String],
//     tags: Label*
//   ): UIO[Boolean] =
//     send(Metric.ServiceCheck(name, status, timestamp, hostname, message, Chunk.fromIterable(tags)))
//
//   override def event(
//     name: String,
//     text: String,
//     timestamp: Option[Long],
//     hostname: Option[String],
//     aggregationKey: Option[String],
//     priority: Option[EventPriority],
//     sourceTypeName: Option[String],
//     alertType: Option[EventAlertType],
//     tags: Label*
//   ): UIO[Boolean] =
//     send(
//       Metric.Event(
//         name,
//         text,
//         timestamp,
//         hostname,
//         aggregationKey,
//         priority,
//         sourceTypeName,
//         alertType,
//         Chunk.fromIterable(tags)
//       )
//     )
//
//   private def send(metric: Metric[_]): UIO[Boolean] = UIO(ring.offer(metric))
//
//   private def shouldSample(rate: Double): Boolean =
//     if (rate >= 1.0 || ThreadLocalRandom.current.nextDouble <= rate) true else false
//
//   private def sample(metrics: Chunk[Metric[_]]): Chunk[Metric[_]] =
//     metrics.filter(m =>
//       m match {
//         case Metric.Counter(_, _, sampleRate, _)   => shouldSample(sampleRate)
//         case Metric.Histogram(_, _, sampleRate, _) => shouldSample(sampleRate)
//         case Metric.Timer(_, _, sampleRate, _)     => shouldSample(sampleRate)
//         case _                                     => true
//       }
//     )
//
//   private[zio] val udp: Chunk[Metric[_]] => Task[Chunk[Long]] =
//     metrics => {
//       val chunks: Chunk[Chunk[Byte]] = sample(metrics)
//         .map(StatsdEncoder.encode)
//         .map(s => s.getBytes())
//         .map(Chunk.fromArray)
//       IO.foreach(chunks)(statsdClient.write)
//     }
//
//   private[zio] val poll: UIO[Chunk[Metric[_]]] =
//     UIO(ring.poll(Metric.Zero)).flatMap {
//       case Metric.Zero => aggregator.get
//       case m @ _       =>
//         aggregator.updateAndGet(_ :+ m)
//     }
//
//   private[zio] def drain: UIO[Unit] =
//     UIO(ring.poll(Metric.Zero)).flatMap {
//       case Metric.Zero => ZIO.unit
//       case m @ _       => aggregator.updateAndGet(_ :+ m) *> drain
//     }
//
//   private val untilNCollected =
//     Schedule.fixed(config.pollRate) *>
//       Schedule.recurUntil[Chunk[Metric[_]]](_.size == config.bufferSize)
//
//   private[zio] val collect: (Chunk[Metric[_]] => Task[Chunk[Long]]) => Task[Chunk[Chunk[Long]]] =
//     f => {
//       for {
//         _             <- poll
//                            .repeat(untilNCollected)
//                            .timeout(config.timeout)
//                            .provide(Has(clock))
//         _             <- drain
//         metrics       <- aggregator.getAndUpdate(_ => Chunk.empty)
//         groupedMetrics = Chunk(metrics.grouped(config.bufferSize).toSeq: _*)
//         l             <- ZIO.foreach(groupedMetrics)(f)
//       } yield l
//     }
//
//   val listen: ZIO[Any, Throwable, Fiber.Runtime[Throwable, Nothing]] = listen(udp)
//
//   def listen(
//     f: Chunk[Metric[_]] => Task[Chunk[Long]]
//   ): ZIO[Any, Throwable, Fiber.Runtime[Throwable, Nothing]] =
//     collect(f).forever.forkDaemon
// }
