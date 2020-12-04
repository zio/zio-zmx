package zio.zmx

import java.nio.charset.StandardCharsets
import java.util.concurrent.ThreadLocalRandom

import zio.clock.Clock
import zio.internal.RingBuffer
import zio.zmx.metrics.Encoder
import zio.{ Chunk, Fiber, RIO, Ref, Schedule, UIO, URIO, URLayer, ZIO, ZLayer, ZManaged }

object MetricsAggregator {

  sealed trait AddResult

  object AddResult {
    object Added   extends AddResult
    object Ignored extends AddResult
    object Dropped extends AddResult
  }

  trait Service {
    def add(m: Metric[_]): UIO[AddResult]
  }

  /**
   * Provides a metrics aggregator that uses a ring buffer for aggregating metrics into chunks of metrics.
   */
  def live(config: MetricsConfig): URLayer[Clock with MetricsSender[Chunk[Byte]], MetricsAggregator] =
    ZLayer.fromManaged(
      ZManaged.makeInterruptible(
        for {
          sender <- ZIO.access[MetricsSender[Chunk[Byte]]](_.get)
          res    <- createRingBufferAggregator(config, sender)
        } yield res
      )(
        _.asInstanceOf[RingBufferAggregator].release
      )
    )

  def encodeMetric(metric: Metric[_]): Chunk[Byte] =
    Chunk.fromArray(Encoder.encode(metric).getBytes(StandardCharsets.UTF_8))

  // TODO: Maybe a chunk of metrics should be encoded into a single chunk of bytes.
  //       -> Add line separators between the single metrics
  def encodeMetrics(metrics: Chunk[Metric[_]]): Chunk[Chunk[Byte]] = metrics.map(encodeMetric)

  private def createRingBufferAggregator(
    config: MetricsConfig,
    sender: MetricsSender.Service[Chunk[Byte]]
  ): URIO[Clock, Service] =
    for {
      ring       <- ZIO.effectTotal(RingBuffer[Metric[_]](config.maximumSize))
      aggregator <- Ref.make[Chunk[Metric[_]]](Chunk.empty)

      collectFiber <- collectIo(config, ring, aggregator, sender).forever.forkDaemon

    } yield new RingBufferAggregator(ring, collectFiber)

  private def collectIo(
    config: MetricsConfig,
    ring: RingBuffer[Metric[_]],
    aggregator: Ref[Chunk[Metric[_]]],
    sender: MetricsSender.Service[Chunk[Byte]]
  ): RIO[Clock, Unit] = {

    val untilNCollected =
      Schedule.fixed(config.pollRate) *>
        Schedule.recurUntil[Chunk[Metric[_]]](_.size == config.bufferSize)

    def poll: URIO[Clock, Chunk[Metric[_]]] =
      UIO(ring.poll(Metric.Zero)).flatMap {
        case Metric.Zero => aggregator.get
        case m @ _       =>
          aggregator.updateAndGet(_ :+ m) >>= { c =>
            if (c.size < config.bufferSize) poll else ZIO.succeedNow(c)
          }
      }

    def drain: UIO[Unit] =
      UIO(ring.poll(Metric.Zero)).flatMap {
        case Metric.Zero => ZIO.unit
        case m @ _       => aggregator.updateAndGet(_ :+ m) *> drain
      }

    for {
      polledMetrics <- poll.repeat(untilNCollected).timeout(config.timeout)
      // drain only if polling did end because of a timeout
      _             <- if (polledMetrics.isEmpty) drain else ZIO.succeedNow(())
      metrics       <- aggregator.getAndUpdate(_ => Chunk.empty)
      // TODO: Each metric is sent as a UDP datagram of its own! Is this really necessary?
      //       I guess the original intent was to send groups of metrics at once.
      //       However, the current code does not implement this.
      groupedMetrics = metrics.grouped(config.bufferSize).flatMap(encodeMetrics).toSeq
      _             <- ZIO.foreach(groupedMetrics)(sender.send(_))
    } yield ()

  }

  private class RingBufferAggregator(
    ring: RingBuffer[Metric[_]],
    collectFiber: Fiber[Throwable, Nothing]
  ) extends Service {

    private def shouldSample(rate: Double): Boolean =
      if (rate >= 1.0 || ThreadLocalRandom.current.nextDouble <= rate) true else false

    private def shouldSample(metric: Metric[_]): Boolean = metric match {
      case Metric.Counter(_, _, sampleRate, _)   => shouldSample(sampleRate)
      case Metric.Histogram(_, _, sampleRate, _) => shouldSample(sampleRate)
      case Metric.Timer(_, _, sampleRate, _)     => shouldSample(sampleRate)
      case _                                     => true
    }

    override def add(metric: Metric[_]): UIO[AddResult] =
      if (shouldSample(metric)) {
        UIO(if (ring.offer(metric)) AddResult.Added else AddResult.Dropped)
      } else {
        ZIO.succeedNow(AddResult.Ignored)
      }

    val release: UIO[Unit] = collectFiber.interrupt.as(())
  }
}
