package zio.zmx.internal

import zio.clock.Clock
import zio.duration._
import zio.test._
import zio.test.environment.{ TestClock, TestEnvironment }
import zio.{ clock, Chunk, Schedule, ZIO }

object ConcurrentSummarySpec extends DefaultRunnableSpec {
  override def spec: ZSpec[TestEnvironment, Any] =
    suite("ConcurrentSummary")(
      testM("single observe works with maxSize = 0") {
        val summary = ConcurrentSummary.manual(maxSize = 0, maxAge = 10.seconds, error = 0.0, quantiles = Chunk.empty)
        val observe = clock.instant.flatMap(now => ZIO.effect(summary.observe(11.0, now)))

        for {
          _        <- observe
          now      <- clock.instant
          snapshot <- ZIO.effect(summary.snapshot(now))
          count    <- ZIO.effect(summary.count())
          sum      <- ZIO.effect(summary.sum())
        } yield assertTrue(
          snapshot.isEmpty,
          count == 1,
          sum == 11.0
        )
      },
      testM("single observe works with arbitrary maxSize") {
        checkM(Gen.int(0, 100000)) { maxSize =>
          val summary = ConcurrentSummary.manual(maxSize, maxAge = 10.seconds, error = 0.0, quantiles = Chunk.empty)
          val observe = clock.instant.flatMap(now => ZIO.effect(summary.observe(11.0, now)))

          for {
            _        <- observe
            now      <- clock.instant
            snapshot <- ZIO.effect(summary.snapshot(now))
            count    <- ZIO.effect(summary.count())
            sum      <- ZIO.effect(summary.sum())
          } yield assertTrue(
            snapshot.length <= 1,
            count == 1,
            sum == 11.0
          )
        }
      },
      zio.test.suite("stable under load")(
        Seq(0, 1, 100, 100000).map { maxSize =>
          testM(s"maxSize = $maxSize") {
            val summary     = ConcurrentSummary.manual(maxSize, maxAge = 10.seconds, error = 0.0, quantiles = Chunk.empty)
            val observe     = clock.instant.flatMap(now => ZIO.effect(summary.observe(11.0, now)))
            val getSnapshot = clock.instant.flatMap(now => ZIO.effect(summary.snapshot(now)))

            val test =
              for {
                f1       <- observe.repeat(Schedule.upTo(2.seconds) *> Schedule.count).forkDaemon
                f2       <- observe.repeat(Schedule.upTo(2.seconds) *> Schedule.count).forkDaemon
                _        <- getSnapshot.repeat(Schedule.upTo(2.seconds))
                snapshot <- getSnapshot
                count    <- ZIO.effect(summary.count())
                sum      <- ZIO.effect(summary.sum())
                f1Count  <- f1.join
                f2Count  <- f2.join
              } yield assertTrue(
                snapshot.length <= maxSize,
                count == (f1Count + f2Count + 2),
                sum == (f1Count + f2Count + 2) * 11.0
              )

            test.provideLayer(Clock.live)
          }
        }: _*
      ),
      testM(s"old measurements not used for quantiles with non-full buffer") {
        val summary            =
          ConcurrentSummary.manual(maxSize = 10, maxAge = 1.seconds, error = 0.0, quantiles = Chunk(0.5, 1.0))
        def observe(v: Double) = clock.instant.flatMap(now => ZIO.effect(summary.observe(v, now)))
        val getSnapshot        = clock.instant.flatMap(now => ZIO.effect(summary.snapshot(now)))

        for {
          _        <- observe(1.0) // old
          _        <- TestClock.adjust(300.millis)
          _        <- observe(2.0) // old
          _        <- TestClock.adjust(300.millis)
          _        <- observe(3.0)
          _        <- TestClock.adjust(300.millis)
          _        <- observe(4.0)
          _        <- TestClock.adjust(300.millis)
          _        <- observe(5.0)
          _        <- TestClock.adjust(300.millis)
          snapshot <- getSnapshot
          count    <- ZIO.effect(summary.count())
          sum      <- ZIO.effect(summary.sum())
        } yield assertTrue(
          snapshot.length == 2,
          snapshot(0) == (0.5, Some(3.0)),
          snapshot(1) == (1.0, Some(5.0)),
          count == 5,
          sum == 1.0 + 2.0 + 3.0 + 4.0 + 5.0
        )
      },
      testM(s"old measurements not used for quantiles with full buffer") {
        val summary            =
          ConcurrentSummary.manual(maxSize = 3, maxAge = 1.seconds, error = 0.0, quantiles = Chunk(0.5, 1.0))
        def observe(v: Double) = clock.instant.flatMap(now => ZIO.effect(summary.observe(v, now)))
        val getSnapshot        = clock.instant.flatMap(now => ZIO.effect(summary.snapshot(now)))

        for {
          _        <- observe(1.0) // old
          _        <- TestClock.adjust(300.millis)
          _        <- observe(2.0) // old
          _        <- TestClock.adjust(300.millis)
          _        <- observe(3.0)
          _        <- TestClock.adjust(300.millis)
          _        <- observe(4.0)
          _        <- TestClock.adjust(300.millis)
          _        <- observe(5.0)
          _        <- TestClock.adjust(300.millis)
          snapshot <- getSnapshot
          count    <- ZIO.effect(summary.count())
          sum      <- ZIO.effect(summary.sum())
        } yield assertTrue(
          snapshot.length == 2,
          snapshot(0) == (0.5, Some(3.0)),
          snapshot(1) == (1.0, Some(5.0)),
          count == 5,
          sum == 1.0 + 2.0 + 3.0 + 4.0 + 5.0
        )
      }
    )
}
